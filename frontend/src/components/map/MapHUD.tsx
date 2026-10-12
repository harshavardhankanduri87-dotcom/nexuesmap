"use client";

import React, { useState } from "react";
import { CampusZone, Coordinates, BackendHealth } from "@/types";
import {
  Shield,
  ShieldAlert,
  Users,
  Radio,
  MapPin,
  ChevronRight,
  ChevronLeft,
  Activity,
  Layers,
  Sparkles,
  Signal,
  Lock,
  MessageSquare,
  Flame,
  Pin
} from "lucide-react";

interface MapHUDProps {
  userCoords: Coordinates | null;
  isGhostMode: boolean;
  onToggleGhostMode: () => void;
  onOpenPrivacyModal?: () => void;
  campusZones: CampusZone[];
  selectedZone: CampusZone | null;
  onSelectZone: (zone: CampusZone) => void;
}

export const MapHUD: React.FC<MapHUDProps> = ({
  userCoords,
  isGhostMode,
  onToggleGhostMode,
  onOpenPrivacyModal,
  campusZones,
  selectedZone,
  onSelectZone,
}) => {
  const [isOpen, setIsOpen] = useState<boolean>(true);
  const [activeTab, setActiveTab] = useState<"zones" | "privacy" | "features">("zones");

  return (
    <aside
      className={`absolute top-4 right-4 z-20 transition-all duration-300 ${
        isOpen ? "w-80 md:w-96" : "w-12"
      }`}
    >
      <div className="glass-panel-glow rounded-2xl overflow-hidden shadow-2xl border border-slate-700/80">
        {/* Toggle Collapse Header */}
        <div className="flex items-center justify-between p-3.5 bg-slate-900/90 border-b border-slate-800">
          {isOpen ? (
            <div className="flex items-center gap-2">
              <Activity className="w-4 h-4 text-cyan-400" />
              <span className="text-xs font-bold tracking-wider text-slate-200 uppercase">
                Geospatial Mesh HUD
              </span>
            </div>
          ) : (
            <div className="w-full flex justify-center">
              <Activity className="w-5 h-5 text-cyan-400 animate-pulse" />
            </div>
          )}

          <button
            type="button"
            onClick={() => setIsOpen(!isOpen)}
            className="p-1 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
            title={isOpen ? "Collapse Telemetry HUD" : "Expand Telemetry HUD"}
          >
            {isOpen ? <ChevronRight className="w-4 h-4" /> : <ChevronLeft className="w-4 h-4" />}
          </button>
        </div>

        {/* HUD Expanded Content */}
        {isOpen && (
          <div className="p-4 space-y-4 max-h-[calc(100vh-160px)] overflow-y-auto">
            
            {/* Quick Navigation Tabs */}
            <div className="grid grid-cols-3 gap-1 bg-slate-950/70 p-1 rounded-xl border border-slate-800">
              <button
                type="button"
                onClick={() => setActiveTab("zones")}
                className={`py-1.5 text-[11px] font-semibold rounded-lg transition-all ${
                  activeTab === "zones"
                    ? "bg-cyan-500/20 text-cyan-300 border border-cyan-500/40 shadow-sm"
                    : "text-slate-400 hover:text-slate-200"
                }`}
              >
                Campus Zones
              </button>
              <button
                type="button"
                onClick={() => setActiveTab("privacy")}
                className={`py-1.5 text-[11px] font-semibold rounded-lg transition-all ${
                  activeTab === "privacy"
                    ? "bg-purple-500/20 text-purple-300 border border-purple-500/40 shadow-sm"
                    : "text-slate-400 hover:text-slate-200"
                }`}
              >
                Privacy Shield
              </button>
              <button
                type="button"
                onClick={() => setActiveTab("features")}
                className={`py-1.5 text-[11px] font-semibold rounded-lg transition-all ${
                  activeTab === "features"
                    ? "bg-emerald-500/20 text-emerald-300 border border-emerald-500/40 shadow-sm"
                    : "text-slate-400 hover:text-slate-200"
                }`}
              >
                Platform Core
              </button>
            </div>

            {/* TAB 1: CAMPUS ZONES */}
            {activeTab === "zones" && (
              <div className="space-y-3">
                <div className="flex items-center justify-between text-xs text-slate-400">
                  <span className="font-mono uppercase text-[10px] tracking-wider">Geofenced Clusters</span>
                  <span className="text-cyan-400 text-[11px] font-semibold">{campusZones.length} Zones Online</span>
                </div>

                <div className="space-y-2">
                  {campusZones.map((zone) => {
                    const isSelected = selectedZone?.id === zone.id;
                    return (
                      <div
                        key={zone.id}
                        onClick={() => onSelectZone(zone)}
                        className={`p-3 rounded-xl cursor-pointer transition-all border ${
                          isSelected
                            ? "bg-cyan-950/40 border-cyan-500/60 shadow-[0_0_15px_rgba(0,240,255,0.15)]"
                            : "bg-slate-900/60 border-slate-800/80 hover:border-slate-700 hover:bg-slate-900"
                        }`}
                      >
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-2">
                            <span
                              className="w-2.5 h-2.5 rounded-full ring-2 ring-slate-950"
                              style={{ backgroundColor: zone.color }}
                            />
                            <h4 className="text-xs font-bold text-slate-200">{zone.name}</h4>
                          </div>
                          <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-slate-950 text-slate-400 border border-slate-800">
                            {zone.code}
                          </span>
                        </div>

                        <p className="text-[11px] text-slate-400 mt-1 line-clamp-1">{zone.description}</p>

                        <div className="flex items-center justify-between mt-2 pt-2 border-t border-slate-800/60 text-[10px] font-mono text-slate-400">
                          <div className="flex items-center gap-1 text-emerald-400">
                            <Users className="w-3 h-3" />
                            <span>{zone.activeUsers} Active Nodes</span>
                          </div>
                          <span className="text-cyan-400 font-medium">Click to Target &rarr;</span>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            {/* TAB 2: PRIVACY & GHOST MODE */}
            {activeTab === "privacy" && (
              <div className="space-y-3">
                <div className="p-3.5 rounded-xl bg-purple-950/20 border border-purple-500/30">
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2">
                      {isGhostMode ? (
                        <ShieldAlert className="w-4 h-4 text-purple-400 animate-pulse" />
                      ) : (
                        <Shield className="w-4 h-4 text-slate-400" />
                      )}
                      <h4 className="text-xs font-bold text-slate-200">Ghost Mode Privacy</h4>
                    </div>
                    <button
                      type="button"
                      onClick={onToggleGhostMode}
                      className={`text-[10px] font-bold px-2 py-1 rounded transition-all ${
                        isGhostMode
                          ? "bg-purple-600 text-white shadow-[0_0_8px_rgba(168,85,247,0.5)]"
                          : "bg-slate-800 text-slate-400 hover:text-white"
                      }`}
                    >
                      {isGhostMode ? "ENABLED" : "DISABLED"}
                    </button>
                  </div>
                  <p className="text-[11px] text-slate-300 leading-relaxed">
                    When active, your broadcast coordinates are obfuscated on the server with a Gaussian fuzzing radius (~100m). Zone-level eligibility is preserved without revealing exact telemetry.
                  </p>
                </div>

                <div className="p-3 rounded-xl bg-slate-900/60 border border-slate-800 space-y-2">
                  <div className="flex items-center justify-between text-[11px]">
                    <span className="text-slate-400 flex items-center gap-1.5">
                      <Lock className="w-3 h-3 text-cyan-400" />
                      Server Fuzzing:
                    </span>
                    <span className="font-mono text-cyan-300">{isGhostMode ? "100m Noise" : "Sub-meter"}</span>
                  </div>
                  <div className="flex items-center justify-between text-[11px]">
                    <span className="text-slate-400 flex items-center gap-1.5">
                      <Signal className="w-3 h-3 text-emerald-400" />
                      WebSocket STOMP:
                    </span>
                    <span className="font-mono text-emerald-400">Secure / TLS</span>
                  </div>
                  <div className="flex items-center justify-between text-[11px]">
                    <span className="text-slate-400 flex items-center gap-1.5">
                      <MapPin className="w-3 h-3 text-cyan-400" />
                      Browser GPS:
                    </span>
                    <span className="font-mono text-cyan-300">
                      {userCoords ? `${userCoords.lat.toFixed(4)}, ${userCoords.lng.toFixed(4)}` : "Acquiring..."}
                    </span>
                  </div>
                </div>

                {onOpenPrivacyModal && (
                  <button
                    type="button"
                    onClick={onOpenPrivacyModal}
                    className="w-full py-2 px-3 rounded-lg text-xs font-semibold bg-slate-800 hover:bg-slate-700 text-cyan-300 border border-slate-700 hover:border-cyan-500/40 flex items-center justify-center gap-1.5 transition-all"
                  >
                    <Shield className="w-3.5 h-3.5 text-cyan-400" />
                    <span>Advanced Privacy Settings</span>
                  </button>
                )}
              </div>
            )}

            {/* TAB 3: PLATFORM ARCHITECTURE ROADMAP */}
            {activeTab === "features" && (
              <div className="space-y-2.5 text-xs">
                <div className="p-2.5 rounded-lg bg-slate-900/60 border border-slate-800 flex items-start gap-2.5">
                  <MessageSquare className="w-4 h-4 text-cyan-400 mt-0.5 shrink-0" />
                  <div>
                    <h5 className="font-semibold text-slate-200 text-[11px]">Geofenced Auto-Chat</h5>
                    <p className="text-[10px] text-slate-400">Users automatically join zone-specific STOMP WebSocket topics upon crossing boundaries.</p>
                  </div>
                </div>

                <div className="p-2.5 rounded-lg bg-slate-900/60 border border-slate-800 flex items-start gap-2.5">
                  <Pin className="w-4 h-4 text-emerald-400 mt-0.5 shrink-0" />
                  <div>
                    <h5 className="font-semibold text-slate-200 text-[11px]">Global Pins &amp; Meetups</h5>
                    <p className="text-[10px] text-slate-400">Post time-decaying campus events, hackathons, and study groups pinned to coordinates.</p>
                  </div>
                </div>

                <div className="p-2.5 rounded-lg bg-slate-900/60 border border-slate-800 flex items-start gap-2.5">
                  <Flame className="w-4 h-4 text-amber-400 mt-0.5 shrink-0" />
                  <div>
                    <h5 className="font-semibold text-slate-200 text-[11px]">Density Heatmaps</h5>
                    <p className="text-[10px] text-slate-400">Aggregated PostGIS density grid for campus logistics without exposing individual users.</p>
                  </div>
                </div>
              </div>
            )}

          </div>
        )}
      </div>
    </aside>
  );
};
