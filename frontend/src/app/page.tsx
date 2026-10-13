"use client";

import React, { useState, useEffect, useCallback } from "react";
import dynamic from "next/dynamic";
import { Navbar } from "@/components/layout/Navbar";
import { MapHUD } from "@/components/map/MapHUD";
import { ZoneChatDrawer } from "@/components/chat/ZoneChatDrawer";
import { PinFilterHUD } from "@/components/pins/PinFilterHUD";
import { PinCreationModal } from "@/components/pins/PinCreationModal";
import { PrivacySettingsModal } from "@/components/privacy/PrivacySettingsModal";
import { CampusZone, Coordinates, PinCategory, PinResponse, PinEvent, PrivacySettings, PeerLocation } from "@/types";
import { Radio } from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { Client } from "@stomp/stompjs";
import { useBrowserGeolocation } from "@/hooks/useBrowserGeolocation";

// Dynamically import NexusMap with SSR disabled to ensure Leaflet loads strictly in client browser
const DynamicNexusMap = dynamic(
  () => import("@/components/map/NexusMap").then((mod) => mod.NexusMap),
  {
    ssr: false,
    loading: () => (
      <div className="w-full h-[calc(100vh-60px)] mt-[60px] flex flex-col items-center justify-center bg-[#090d16] text-cyan-400">
        <div className="relative flex items-center justify-center w-16 h-16 rounded-2xl bg-cyan-950/40 border border-cyan-500/30 mb-4">
          <Radio className="w-8 h-8 text-cyan-400 animate-pulse" />
          <div className="absolute inset-0 rounded-2xl border border-cyan-400/20 animate-ping" />
        </div>
        <p className="text-sm font-mono tracking-widest text-slate-300 uppercase">
          Initializing Cartographic Engine...
        </p>
        <p className="text-xs text-slate-500 mt-1 font-mono">
          Loading OpenStreetMap Vector Layers &amp; Geofences
        </p>
      </div>
    ),
  }
);

// Fallback Prototype Campus Geofenced Zones (mapped 1:1 to PostGIS DB IDs)
const DEFAULT_CAMPUS_ZONES: CampusZone[] = [
  {
    id: "1",
    name: "Campus Perimeter Boundary",
    code: "ZONE-PERIMETER",
    center: [37.7750, -122.4190],
    polygon: [
      [37.7680, -122.4300],
      [37.7680, -122.4080],
      [37.7840, -122.4080],
      [37.7840, -122.4300],
    ],
    color: "#38bdf8",
    activeUsers: 145,
    description: "Overall designated perimeter of the prototype academic campus grounds.",
  },
  {
    id: "2",
    name: "Main Gate & Welcome Transit Plaza",
    code: "ZONE-MAIN-GATE",
    center: [37.7710, -122.4245],
    polygon: [
      [37.7695, -122.4265],
      [37.7695, -122.4225],
      [37.7725, -122.4225],
      [37.7725, -122.4265],
    ],
    color: "#22c55e",
    activeUsers: 34,
    description: "Primary campus entrance, transit hub, and shuttle pickup area.",
  },
  {
    id: "3",
    name: "Central Academic Library",
    code: "ZONE-LIBRARY",
    center: [37.7745, -122.4200],
    polygon: [
      [37.7730, -122.4220],
      [37.7730, -122.4180],
      [37.7760, -122.4180],
      [37.7760, -122.4220],
    ],
    color: "#eab308",
    activeUsers: 68,
    description: "University archive collections, quiet study rooms, and research commons.",
  },
  {
    id: "4",
    name: "Student Canteen & Dining Commons",
    code: "ZONE-CANTEEN",
    center: [37.7785, -122.4200],
    polygon: [
      [37.7770, -122.4220],
      [37.7770, -122.4180],
      [37.7800, -122.4180],
      [37.7800, -122.4220],
    ],
    color: "#f97316",
    activeUsers: 82,
    description: "Food hall, dining commons, and student social terrace.",
  },
  {
    id: "5",
    name: "Innovation Lab & Tech Maker Studio",
    code: "ZONE-INNOVATION-LAB",
    center: [37.7775, -122.4145],
    polygon: [
      [37.7760, -122.4165],
      [37.7760, -122.4125],
      [37.7790, -122.4125],
      [37.7790, -122.4165],
    ],
    color: "#00f0ff",
    activeUsers: 45,
    description: "Advanced hardware prototyping lab, AI incubation lab, and collaborative space.",
  },
  {
    id: "6",
    name: "Grand University Auditorium",
    code: "ZONE-AUDITORIUM",
    center: [37.7785, -122.4250],
    polygon: [
      [37.7770, -122.4270],
      [37.7770, -122.4230],
      [37.7800, -122.4230],
      [37.7800, -122.4270],
    ],
    color: "#a855f7",
    activeUsers: 95,
    description: "Keynote conference center, cultural theater, and hackathon hall.",
  },
];

export default function HomePage() {
  const { isAuthenticated } = useAuth();
  const [campusZones, setCampusZones] = useState<CampusZone[]>(DEFAULT_CAMPUS_ZONES);
  const {
    coords: userCoords,
    status: locationStatus,
    error: locationError,
    recenterTrigger,
    requestLocation,
    recenter,
  } = useBrowserGeolocation();
  const [isGhostMode, setIsGhostMode] = useState<boolean>(false);
  const [selectedZone, setSelectedZone] = useState<CampusZone | null>(DEFAULT_CAMPUS_ZONES[2]); // Central Library
  const [isChatOpen, setIsChatOpen] = useState<boolean>(false);

  // Global Pins State
  const [pins, setPins] = useState<PinResponse[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<PinCategory | "ALL">("ALL");
  const [radiusMeters, setRadiusMeters] = useState<number>(5000); // 5km default
  const [isDropPinMode, setIsDropPinMode] = useState<boolean>(false);
  const [droppedPinCoords, setDroppedPinCoords] = useState<{ lat: number; lng: number } | null>(null);
  const [isPinModalOpen, setIsPinModalOpen] = useState<boolean>(false);

  // Privacy & Peer Telemetry State
  const [privacySettings, setPrivacySettings] = useState<PrivacySettings | null>(null);
  const [isPrivacyModalOpen, setIsPrivacyModalOpen] = useState<boolean>(false);
  const [peerLocations, setPeerLocations] = useState<PeerLocation[]>([]);

  // Fetch active zones from PostGIS backend
  useEffect(() => {
    const fetchZones = async () => {
      try {
        const res = await fetch("/api/zones");
        if (res.ok) {
          const data = await res.json();
          if (Array.isArray(data) && data.length > 0) {
            const mappedZones: CampusZone[] = data.map((z: any) => ({
              id: String(z.id),
              name: z.name,
              code: z.code,
              center: [z.centerLat, z.centerLng],
              polygon: z.leafletCoordinates || [],
              color: z.color || "#00f0ff",
              activeUsers: z.activeUsers || 0,
              description: z.description || "",
            }));
            setCampusZones(mappedZones);
            const facility = mappedZones.find((z) => z.code !== "ZONE-PERIMETER") || mappedZones[0];
            setSelectedZone(facility);
          }
        }
      } catch (e) {
        // Fallback to default
      }
    };

    fetchZones();
  }, []);

  // Fetch Nearby Pins
  const fetchNearbyPins = useCallback(async () => {
    if (!userCoords) return;
    try {
      const url = new URL("/api/pins/nearby", window.location.origin);
      url.searchParams.set("lat", String(userCoords.lat));
      url.searchParams.set("lng", String(userCoords.lng));
      url.searchParams.set("radiusMeters", String(radiusMeters));
      if (selectedCategory !== "ALL") {
        url.searchParams.set("category", selectedCategory);
      }
      url.searchParams.set("page", "0");
      url.searchParams.set("size", "50");

      const res = await fetch(url.toString(), {
        credentials: "include",
      });

      if (res.ok) {
        const data = await res.json();
        setPins(data.pins || []);
      }
    } catch (err) {
      console.warn("Failed to fetch nearby pins", err);
    }
  }, [userCoords, radiusMeters, selectedCategory]);

  useEffect(() => {
    fetchNearbyPins();
  }, [fetchNearbyPins]);

  // Real-Time Global Pin WebSocket Subscription (/topic/pins)
  useEffect(() => {
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

    const client = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe("/topic/pins", (msg) => {
          try {
            const event: PinEvent = JSON.parse(msg.body);
            if (event.eventType === "PIN_CREATED" && event.pin) {
              setPins((prev) => {
                if (prev.some((p) => p.id === event.pin!.id)) return prev;
                return [event.pin!, ...prev];
              });
            } else if (
              (event.eventType === "PIN_UPDATED" ||
                event.eventType === "MEETUP_JOINED" ||
                event.eventType === "MEETUP_LEFT") &&
              event.pin
            ) {
              setPins((prev) =>
                prev.map((p) => (p.id === event.pin!.id ? event.pin! : p))
              );
            } else if (event.eventType === "PIN_DELETED") {
              setPins((prev) => prev.filter((p) => p.id !== event.pinId));
            }
          } catch (err) {
            console.error("Malformed pin event payload", err);
          }
        });
      },
    });

    client.activate();

    return () => {
      if (client.active) {
        client.deactivate();
      }
    };
  }, []);

  // Synchronize real browser GPS updates with backend PostGIS spatial engine when authenticated
  useEffect(() => {
    if (!userCoords || !isAuthenticated) return;

    const syncLocation = async () => {
      try {
        const res = await fetch("/api/location/update", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          credentials: "include",
          body: JSON.stringify({
            latitude: userCoords.lat,
            longitude: userCoords.lng,
            accuracy: userCoords.accuracy || 10,
          }),
        });

        if (res.ok) {
          const data = await res.json();
          if (data.currentZone) {
            const match = campusZones.find((z) => z.code === data.currentZone.code);
            if (match) setSelectedZone(match);
          }
        }
      } catch (err) {
        // Non-blocking telemetry sync
      }
    };

    syncLocation();
  }, [userCoords, isAuthenticated, campusZones]);

  // Fetch user privacy settings on login
  useEffect(() => {
    if (!isAuthenticated) return;

    const fetchPrivacySettings = async () => {
      try {
        const res = await fetch("/api/privacy/settings", { credentials: "include" });
        if (res.ok) {
          const data: PrivacySettings = await res.json();
          setPrivacySettings(data);
          setIsGhostMode(data.ghostMode);
        }
      } catch (err) {
        // Non-blocking telemetry
      }
    };

    fetchPrivacySettings();
  }, [isAuthenticated]);

  // Fetch peer locations inside active campus zone (fuzzed server-side coordinates)
  useEffect(() => {
    if (!isAuthenticated || isGhostMode || !selectedZone) {
      setPeerLocations([]);
      return;
    }

    const fetchPeers = async () => {
      try {
        const numericZoneId = selectedZone.id ? parseInt(selectedZone.id, 10) : 3;
        const res = await fetch(`/api/privacy/peers?zoneId=${numericZoneId || 3}`, {
          credentials: "include",
        });
        if (res.ok) {
          const data: PeerLocation[] = await res.json();
          setPeerLocations(data);
        }
      } catch (err) {
        // Non-blocking telemetry
      }
    };

    fetchPeers();
    const interval = setInterval(fetchPeers, 10000);
    return () => clearInterval(interval);
  }, [isAuthenticated, isGhostMode, selectedZone]);

  const handleSavePrivacySettings = async (updated: Partial<PrivacySettings>): Promise<boolean> => {
    try {
      const res = await fetch("/api/privacy/settings", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(updated),
      });

      if (res.ok) {
        const data: PrivacySettings = await res.json();
        setPrivacySettings(data);
        setIsGhostMode(data.ghostMode);
        return true;
      }
      return false;
    } catch {
      return false;
    }
  };

  const handleToggleGhostMode = async () => {
    const nextVal = !isGhostMode;
    setIsGhostMode(nextVal);
    if (isAuthenticated) {
      await handleSavePrivacySettings({ ghostMode: nextVal });
    }
  };

  // Meetup actions
  const handleJoinMeetup = async (pinId: number) => {
    try {
      const res = await fetch(`/api/pins/${pinId}/join`, {
        method: "POST",
        credentials: "include",
      });
      if (res.ok) {
        fetchNearbyPins();
      } else {
        const err = await res.json().catch(() => ({}));
        alert(err.message || "Failed to join meetup.");
      }
    } catch (e: any) {
      alert("Network error: " + e.message);
    }
  };

  const handleLeaveMeetup = async (pinId: number) => {
    try {
      const res = await fetch(`/api/pins/${pinId}/leave`, {
        method: "POST",
        credentials: "include",
      });
      if (res.ok) {
        fetchNearbyPins();
      }
    } catch (e: any) {
      alert("Network error: " + e.message);
    }
  };

  const handleDeletePin = async (pinId: number) => {
    if (!confirm("Are you sure you want to delete this pin?")) return;
    try {
      const res = await fetch(`/api/pins/${pinId}`, {
        method: "DELETE",
        credentials: "include",
      });
      if (res.ok) {
        setPins((prev) => prev.filter((p) => p.id !== pinId));
      }
    } catch (e: any) {
      alert("Network error: " + e.message);
    }
  };

  // Map Click for Pin Placement
  const handleMapClickForPin = (coords: { lat: number; lng: number }) => {
    setDroppedPinCoords(coords);
    setIsPinModalOpen(true);
    setIsDropPinMode(false);
  };

  const handlePinCreated = (newPin: PinResponse) => {
    setPins((prev) => [newPin, ...prev]);
  };

  return (
    <main className="relative min-h-screen w-full bg-[#090d16] overflow-hidden">
      {/* Top Navbar */}
      <Navbar
        isGhostMode={isGhostMode}
        onToggleGhostMode={handleToggleGhostMode}
        onOpenPrivacyModal={() => setIsPrivacyModalOpen(true)}
        activeZoneName={selectedZone ? selectedZone.name : "Global Campus Perimeter"}
        userCoords={userCoords}
        locationStatus={locationStatus}
      />

      {/* Main Interactive Map Surface with Pins & Active Peers */}
      <DynamicNexusMap
        userCoords={userCoords}
        locationStatus={locationStatus}
        locationError={locationError}
        onRequestLocation={requestLocation}
        onRecenterLocation={recenter}
        recenterTrigger={recenterTrigger}
        isGhostMode={isGhostMode}
        selectedZone={selectedZone}
        onSelectZone={setSelectedZone}
        campusZones={campusZones}
        pins={pins}
        peerLocations={peerLocations}
        isDropPinMode={isDropPinMode}
        onMapClickForPin={handleMapClickForPin}
        onJoinMeetup={handleJoinMeetup}
        onLeaveMeetup={handleLeaveMeetup}
        onDeletePin={handleDeletePin}
      />

      {/* Pin Filter Radar HUD */}
      <PinFilterHUD
        selectedCategory={selectedCategory}
        onSelectCategory={setSelectedCategory}
        radiusMeters={radiusMeters}
        onChangeRadius={setRadiusMeters}
        isDropPinMode={isDropPinMode}
        onToggleDropPinMode={() => setIsDropPinMode((prev) => !prev)}
        pinCount={pins.length}
      />

      {/* Floating Geospatial Telemetry HUD */}
      <MapHUD
        userCoords={userCoords}
        isGhostMode={isGhostMode}
        onToggleGhostMode={handleToggleGhostMode}
        onOpenPrivacyModal={() => setIsPrivacyModalOpen(true)}
        campusZones={campusZones}
        selectedZone={selectedZone}
        onSelectZone={setSelectedZone}
      />

      {/* Real-Time Geofenced Zone Chat Drawer */}
      <ZoneChatDrawer
        selectedZone={selectedZone}
        isGhostMode={isGhostMode}
        isOpen={isChatOpen}
        onToggle={() => setIsChatOpen((prev) => !prev)}
      />

      {/* Pin Creation Modal */}
      <PinCreationModal
        isOpen={isPinModalOpen}
        onClose={() => setIsPinModalOpen(false)}
        coords={droppedPinCoords}
        onPinCreated={handlePinCreated}
      />

      {/* Privacy & Anti-Averaging Settings Modal */}
      <PrivacySettingsModal
        isOpen={isPrivacyModalOpen}
        onClose={() => setIsPrivacyModalOpen(false)}
        currentSettings={privacySettings}
        onSaveSettings={handleSavePrivacySettings}
      />
    </main>
  );
}
