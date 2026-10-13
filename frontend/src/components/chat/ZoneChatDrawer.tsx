"use client";

import React, { useState, useEffect, useRef, useCallback } from "react";
import { CampusZone, ChatMessage, PresenceEvent, PaginatedMessagesResponse } from "@/types";
import { useAuth } from "@/context/AuthContext";
import { Client, IMessage } from "@stomp/stompjs";
import {
  MessageSquare,
  Send,
  Users,
  Shield,
  ShieldAlert,
  Wifi,
  WifiOff,
  AlertCircle,
  X,
  ChevronDown,
  ChevronUp,
  RefreshCw,
  Lock,
} from "lucide-react";

interface ZoneChatDrawerProps {
  selectedZone: CampusZone | null;
  isGhostMode: boolean;
  isOpen: boolean;
  onToggle: () => void;
}

export const ZoneChatDrawer: React.FC<ZoneChatDrawerProps> = ({
  selectedZone,
  isGhostMode,
  isOpen,
  onToggle,
}) => {
  const { user, isAuthenticated } = useAuth();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputText, setInputText] = useState<string>("");
  const [connectionStatus, setConnectionStatus] = useState<
    "CONNECTED" | "CONNECTING" | "DISCONNECTED" | "UNAUTHORIZED"
  >("DISCONNECTED");
  const [activeMemberCount, setActiveMemberCount] = useState<number>(0);
  const [presenceToast, setPresenceToast] = useState<string | null>(null);
  const [isLoadingHistory, setIsLoadingHistory] = useState<boolean>(false);
  const [sendError, setSendError] = useState<string | null>(null);
  const [isSending, setIsSending] = useState<boolean>(false);

  const stompClientRef = useRef<Client | null>(null);
  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const activeSubRef = useRef<any>(null);
  const presenceSubRef = useRef<any>(null);

  // Derive numeric zone ID
  const zoneNumericId = React.useMemo(() => {
    if (!selectedZone) return null;
    if (typeof (selectedZone as any).id === "number") return (selectedZone as any).id;
    if (selectedZone.code === "ZONE-PERIMETER") return 1;
    if (selectedZone.code === "ZONE-MAIN-GATE") return 2;
    if (selectedZone.code === "ZONE-LIBRARY") return 3;
    if (selectedZone.code === "ZONE-CANTEEN") return 4;
    if (selectedZone.code === "ZONE-INNOVATION-LAB") return 5;
    if (selectedZone.code === "ZONE-AUDITORIUM") return 6;
    const match = String(selectedZone.id).match(/\d+/);
    return match ? parseInt(match[0], 10) : 1;
  }, [selectedZone]);

  const scrollToBottom = useCallback((smooth = true) => {
    if (messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({
        behavior: smooth ? "smooth" : "auto",
      });
    }
  }, []);

  // Fetch paginated message history
  const loadMessageHistory = useCallback(
    async (zoneId: number) => {
      setIsLoadingHistory(true);
      setSendError(null);
      try {
        const res = await fetch(`/api/zones/${zoneId}/messages?page=0&size=40`, {
          credentials: "include",
        });

        if (res.ok) {
          const data: PaginatedMessagesResponse = await res.json();
          // Backend returns DESC; sort ASC for display timeline
          const sorted = [...(data.messages || [])].sort(
            (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
          );
          setMessages(sorted);
          setTimeout(() => scrollToBottom(false), 100);
        } else if (res.status === 403) {
          setConnectionStatus("UNAUTHORIZED");
          setSendError("Zone restricted: You must be physically inside this geofenced area.");
        }
      } catch (err) {
        console.warn("Failed to load message history:", err);
      } finally {
        setIsLoadingHistory(false);
      }
    },
    [scrollToBottom]
  );

  // Setup WebSocket / STOMP Connection
  useEffect(() => {
    if (!zoneNumericId || !isAuthenticated) {
      setConnectionStatus("DISCONNECTED");
      return;
    }

    loadMessageHistory(zoneNumericId);

    const wsProtocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const defaultWsPort =
      window.location.port === "3000"
        ? ":8080"
        : window.location.port
        ? `:${window.location.port}`
        : "";
    const wsUrl =
      process.env.NEXT_PUBLIC_WS_URL ||
      `${wsProtocol}//${window.location.hostname}${defaultWsPort}/ws`;

    const token = typeof window !== "undefined" ? localStorage.getItem("nexus_jwt") : null;
    const client = new Client({
      brokerURL: wsUrl,
      connectHeaders: token
        ? {
            Authorization: `Bearer ${token}`,
            token: token,
          }
        : {},
      reconnectDelay: 4000,
      heartbeatIncoming: 5000,
      heartbeatOutgoing: 5000,
      onConnect: () => {
        setConnectionStatus("CONNECTED");
        setSendError(null);

        // Subscribe to zone chat messages
        activeSubRef.current = client.subscribe(
          `/topic/zone.${zoneNumericId}`,
          (message: IMessage) => {
            try {
              const incoming: ChatMessage = JSON.parse(message.body);
              setMessages((prev) => {
                if (prev.some((m) => m.id === incoming.id)) return prev;
                return [...prev, incoming];
              });
              setTimeout(() => scrollToBottom(true), 50);
            } catch (err) {
              console.error("Malformed STOMP chat message payload", err);
            }
          }
        );

        // Subscribe to zone live presence events
        presenceSubRef.current = client.subscribe(
          `/topic/zone.${zoneNumericId}.presence`,
          (message: IMessage) => {
            try {
              const event: PresenceEvent = JSON.parse(message.body);
              setActiveMemberCount(event.activeMemberCount);
              const label = event.isGhostMode ? "Ghost Operator" : event.userName;
              const action = event.eventType === "JOIN" ? "entered" : "left";
              setPresenceToast(`${label} ${action} the zone`);
              setTimeout(() => setPresenceToast(null), 3500);
            } catch (err) {
              console.error("Malformed presence event payload", err);
            }
          }
        );
      },
      onStompError: (frame) => {
        const errorMsg = frame.headers["message"] || frame.body || "";
        if (
          errorMsg.toLowerCase().includes("unauthorized") ||
          errorMsg.toLowerCase().includes("access denied") ||
          errorMsg.toLowerCase().includes("not actively located")
        ) {
          setConnectionStatus("UNAUTHORIZED");
          setSendError("Access Denied: Geofence perimeter verification failed.");
        } else {
          setSendError("Real-time channel error: " + errorMsg);
        }
      },
      onWebSocketClose: () => {
        if (connectionStatus !== "UNAUTHORIZED") {
          setConnectionStatus("DISCONNECTED");
        }
      },
    });

    setConnectionStatus("CONNECTING");
    client.activate();
    stompClientRef.current = client;

    return () => {
      if (activeSubRef.current) activeSubRef.current.unsubscribe();
      if (presenceSubRef.current) presenceSubRef.current.unsubscribe();
      if (client.active) {
        client.deactivate();
      }
    };
  }, [zoneNumericId, isAuthenticated, loadMessageHistory, scrollToBottom]);

  // Handle message sending (via STOMP with REST fallback)
  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputText.trim() || !zoneNumericId || isSending) return;

    const trimmed = inputText.trim();
    if (trimmed.length > 1000) {
      setSendError("Message length exceeds maximum 1,000 characters limit.");
      return;
    }

    setIsSending(true);
    setSendError(null);

    const client = stompClientRef.current;
    if (client && client.connected && connectionStatus === "CONNECTED") {
      try {
        client.publish({
          destination: `/app/zone.${zoneNumericId}.send`,
          body: JSON.stringify({ content: trimmed }),
        });
        setInputText("");
        setIsSending(false);
        return;
      } catch (err) {
        console.warn("STOMP send failed, attempting REST fallback", err);
      }
    }

    // REST fallback
    try {
      const res = await fetch(`/api/zones/${zoneNumericId}/messages`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify({ content: trimmed }),
      });

      if (res.ok) {
        const savedMessage: ChatMessage = await res.json();
        setMessages((prev) => {
          if (prev.some((m) => m.id === savedMessage.id)) return prev;
          return [...prev, savedMessage];
        });
        setInputText("");
        setTimeout(() => scrollToBottom(true), 50);
      } else if (res.status === 429) {
        setSendError("Rate limit exceeded: Max 5 messages every 3 seconds.");
      } else if (res.status === 403) {
        setSendError("Forbidden: You must be physically inside this zone to post.");
      } else {
        const errorData = await res.json().catch(() => ({}));
        setSendError(errorData.message || "Failed to transmit message.");
      }
    } catch (err: any) {
      setSendError("Network transmission error: " + (err.message || "Failed to reach server"));
    } finally {
      setIsSending(false);
    }
  };

  const formatTimestamp = (isoDate: string) => {
    try {
      const d = new Date(isoDate);
      return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    } catch {
      return "";
    }
  };

  return (
    <aside
      className={`fixed bottom-4 left-4 z-30 transition-all duration-300 font-sans ${
        isOpen ? "w-[92vw] sm:w-[420px] h-[540px]" : "w-auto h-auto"
      }`}
    >
      {!isOpen ? (
        <button
          id="zone-chat-toggle-btn"
          type="button"
          onClick={onToggle}
          className="flex items-center gap-2.5 px-4 py-3 rounded-2xl bg-slate-900/90 hover:bg-slate-800 text-cyan-400 border border-cyan-500/40 shadow-2xl backdrop-blur-xl transition-all hover:scale-105 active:scale-95"
        >
          <div className="relative">
            <MessageSquare className="w-5 h-5 text-cyan-400" />
            <span
              className={`absolute -top-1 -right-1 w-2.5 h-2.5 rounded-full ring-2 ring-slate-900 ${
                connectionStatus === "CONNECTED"
                  ? "bg-emerald-400 animate-pulse"
                  : connectionStatus === "CONNECTING"
                  ? "bg-amber-400"
                  : "bg-rose-500"
              }`}
            />
          </div>
          <span className="text-xs font-bold tracking-wide uppercase text-slate-200">
            {selectedZone ? selectedZone.name : "Zone Comms"}
          </span>
        </button>
      ) : (
        <div className="flex flex-col h-full rounded-2xl overflow-hidden bg-slate-950/95 border border-slate-800/90 shadow-2xl backdrop-blur-2xl">
          {/* Header */}
          <div className="flex items-center justify-between p-3.5 bg-slate-900/90 border-b border-slate-800">
            <div className="flex items-center gap-2.5 min-w-0">
              <span
                className="w-3 h-3 rounded-full shrink-0 ring-2 ring-slate-950"
                style={{ backgroundColor: selectedZone?.color || "#00f0ff" }}
              />
              <div className="min-w-0">
                <h3 className="text-xs font-bold text-slate-100 truncate">
                  {selectedZone ? selectedZone.name : "Geofenced Channel"}
                </h3>
                <div className="flex items-center gap-2 mt-0.5">
                  <span className="text-[10px] font-mono text-cyan-400 bg-cyan-950/60 px-1.5 py-0.2 rounded border border-cyan-800/40">
                    {selectedZone?.code || "ZONE"}
                  </span>
                  <div className="flex items-center gap-1 text-[10px] text-slate-400">
                    <Users className="w-3 h-3 text-emerald-400" />
                    <span>{activeMemberCount} online</span>
                  </div>
                </div>
              </div>
            </div>

            <div className="flex items-center gap-2 shrink-0">
              {/* Connection Pill */}
              <div
                className={`flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-mono border ${
                  connectionStatus === "CONNECTED"
                    ? "bg-emerald-950/50 text-emerald-300 border-emerald-500/30"
                    : connectionStatus === "CONNECTING"
                    ? "bg-amber-950/50 text-amber-300 border-amber-500/30"
                    : connectionStatus === "UNAUTHORIZED"
                    ? "bg-rose-950/50 text-rose-300 border-rose-500/30"
                    : "bg-slate-900 text-slate-400 border-slate-700"
                }`}
                title={`STOMP Status: ${connectionStatus}`}
              >
                {connectionStatus === "CONNECTED" ? (
                  <Wifi className="w-3 h-3 text-emerald-400" />
                ) : connectionStatus === "UNAUTHORIZED" ? (
                  <Lock className="w-3 h-3 text-rose-400" />
                ) : (
                  <WifiOff className="w-3 h-3 text-slate-400" />
                )}
                <span>{connectionStatus}</span>
              </div>

              <button
                type="button"
                onClick={onToggle}
                className="p-1 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
                title="Minimize Channel"
              >
                <ChevronDown className="w-4 h-4" />
              </button>
            </div>
          </div>

          {/* Real-time Presence Toast */}
          {presenceToast && (
            <div className="px-3 py-1 bg-cyan-950/70 border-b border-cyan-800/40 text-[11px] text-cyan-300 flex items-center gap-1.5 animate-fadeIn">
              <span className="w-1.5 h-1.5 rounded-full bg-cyan-400 animate-ping" />
              <span>{presenceToast}</span>
            </div>
          )}

          {/* Privacy HUD Banner */}
          {isGhostMode && (
            <div className="px-3 py-1.5 bg-purple-950/40 border-b border-purple-800/40 text-[10px] text-purple-300 flex items-center justify-between">
              <span className="flex items-center gap-1">
                <ShieldAlert className="w-3.5 h-3.5 text-purple-400 animate-pulse" />
                <span>Ghost Mode active: Messages posted as Ghost Operator</span>
              </span>
              <span className="font-mono text-[9px] uppercase tracking-wider text-purple-400">Cloaked</span>
            </div>
          )}

          {/* Message List */}
          <div className="flex-1 p-3 space-y-3 overflow-y-auto bg-slate-950/40">
            {isLoadingHistory && (
              <div className="flex items-center justify-center py-6 text-slate-500 gap-2 text-xs font-mono">
                <RefreshCw className="w-3.5 h-3.5 animate-spin text-cyan-400" />
                <span>Synchronizing Zone Messages...</span>
              </div>
            )}

            {!isLoadingHistory && messages.length === 0 && (
              <div className="flex flex-col items-center justify-center h-full text-center p-6 text-slate-500">
                <MessageSquare className="w-8 h-8 text-slate-600 mb-2" />
                <p className="text-xs font-semibold text-slate-400">No transmissions in this zone yet</p>
                <p className="text-[11px] text-slate-600 mt-1">
                  Transmit the first message to members physically present in {selectedZone?.name}.
                </p>
              </div>
            )}

            {messages.map((msg) => {
              const isMe = user && msg.senderId === user.id;
              const isGhost = msg.isGhostMode;

              return (
                <div
                  key={msg.id}
                  className={`flex flex-col ${isMe ? "items-end" : "items-start"} space-y-1`}
                >
                  <div className="flex items-center gap-1.5 text-[10px] text-slate-400 px-1">
                    {isGhost ? (
                      <span className="flex items-center gap-1 text-purple-400 font-semibold">
                        <Shield className="w-2.5 h-2.5" />
                        Ghost Operator
                      </span>
                    ) : (
                      <span className="font-medium text-slate-300">{msg.senderName}</span>
                    )}
                    <span className="text-slate-600 font-mono text-[9px]">
                      {formatTimestamp(msg.createdAt)}
                    </span>
                  </div>

                  <div
                    className={`max-w-[85%] rounded-2xl px-3.5 py-2 text-xs leading-relaxed break-words shadow-md ${
                      isMe
                        ? "bg-gradient-to-br from-cyan-600 to-blue-600 text-white rounded-br-sm"
                        : isGhost
                        ? "bg-purple-950/50 text-purple-100 border border-purple-500/40 rounded-bl-sm"
                        : "bg-slate-900/90 text-slate-200 border border-slate-800 rounded-bl-sm"
                    }`}
                  >
                    {msg.content}
                  </div>
                </div>
              );
            })}
            <div ref={messagesEndRef} />
          </div>

          {/* Sending Error Alert */}
          {sendError && (
            <div className="px-3 py-1.5 bg-rose-950/80 border-t border-rose-800/60 text-[11px] text-rose-300 flex items-center justify-between">
              <span className="flex items-center gap-1.5">
                <AlertCircle className="w-3.5 h-3.5 text-rose-400 shrink-0" />
                <span className="truncate">{sendError}</span>
              </span>
              <button
                type="button"
                onClick={() => setSendError(null)}
                className="text-rose-400 hover:text-white p-0.5"
              >
                <X className="w-3 h-3" />
              </button>
            </div>
          )}

          {/* Input Box Form */}
          <form
            onSubmit={handleSendMessage}
            className="p-2.5 bg-slate-900/95 border-t border-slate-800 flex items-center gap-2"
          >
            <div className="relative flex-1">
              <input
                id="zone-chat-input"
                type="text"
                value={inputText}
                onChange={(e) => setInputText(e.target.value)}
                placeholder={
                  !isAuthenticated
                    ? "Log in to transmit messages..."
                    : connectionStatus === "UNAUTHORIZED"
                    ? "Zone membership required to transmit..."
                    : `Message ${selectedZone?.name || "zone"}...`
                }
                disabled={!isAuthenticated || connectionStatus === "UNAUTHORIZED" || isSending}
                maxLength={1000}
                className="w-full bg-slate-950/80 border border-slate-800 focus:border-cyan-500 rounded-xl px-3 py-2 text-xs text-slate-100 placeholder-slate-500 outline-none transition-all disabled:opacity-50 disabled:cursor-not-allowed"
              />
              {inputText.length > 800 && (
                <span className="absolute right-2.5 bottom-2 text-[9px] font-mono text-amber-400">
                  {1000 - inputText.length}
                </span>
              )}
            </div>

            <button
              id="zone-chat-send-btn"
              type="submit"
              disabled={
                !inputText.trim() ||
                !isAuthenticated ||
                connectionStatus === "UNAUTHORIZED" ||
                isSending
              }
              className="p-2.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 active:scale-95 disabled:bg-slate-800 disabled:text-slate-600 text-white transition-all shadow-md shrink-0 flex items-center justify-center"
              title="Transmit to Zone Channel"
            >
              {isSending ? (
                <RefreshCw className="w-4 h-4 animate-spin text-cyan-200" />
              ) : (
                <Send className="w-4 h-4" />
              )}
            </button>
          </form>
        </div>
      )}
    </aside>
  );
};
