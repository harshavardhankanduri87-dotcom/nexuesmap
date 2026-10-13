"use client";

import React, { useEffect, useRef, useState } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { CampusZone, Coordinates, PinResponse, PeerLocation, GeolocationStatus } from "@/types";
import { 
  Compass, 
  Crosshair, 
  Layers, 
  AlertTriangle,
  Zap,
} from "lucide-react";

// Fix default leaflet icon paths for Next.js bundler
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon-2x.png",
  iconUrl: "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon.png",
  shadowUrl: "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png",
});

export interface NexusMapProps {
  userCoords: Coordinates | null;
  locationStatus: GeolocationStatus;
  locationError: string | null;
  onRequestLocation: () => void;
  onRecenterLocation: () => void;
  recenterTrigger?: number;
  isGhostMode: boolean;
  selectedZone: CampusZone | null;
  onSelectZone: (zone: CampusZone) => void;
  campusZones: CampusZone[];
  pins?: PinResponse[];
  peerLocations?: PeerLocation[];
  isDropPinMode?: boolean;
  onMapClickForPin?: (coords: { lat: number; lng: number }) => void;
  onJoinMeetup?: (pinId: number) => void;
  onLeaveMeetup?: (pinId: number) => void;
  onDeletePin?: (pinId: number) => void;
}

export const NexusMap: React.FC<NexusMapProps> = ({
  userCoords,
  locationStatus,
  locationError,
  onRequestLocation,
  onRecenterLocation,
  recenterTrigger = 0,
  isGhostMode,
  selectedZone,
  onSelectZone,
  campusZones,
  pins = [],
  peerLocations = [],
  isDropPinMode = false,
  onMapClickForPin,
  onJoinMeetup,
  onLeaveMeetup,
  onDeletePin,
}) => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const userMarkerRef = useRef<L.CircleMarker | null>(null);
  const accuracyCircleRef = useRef<L.Circle | null>(null);
  const polygonsGroupRef = useRef<L.LayerGroup | null>(null);
  const pinsGroupRef = useRef<L.LayerGroup | null>(null);
  const peersGroupRef = useRef<L.LayerGroup | null>(null);
  const hasCenteredInitiallyRef = useRef<boolean>(false);
  
  const isDropPinModeRef = useRef<boolean>(isDropPinMode);
  const onMapClickForPinRef = useRef<typeof onMapClickForPin>(onMapClickForPin);

  useEffect(() => {
    isDropPinModeRef.current = isDropPinMode;
    onMapClickForPinRef.current = onMapClickForPin;
    if (mapContainerRef.current) {
      if (isDropPinMode) {
        mapContainerRef.current.style.cursor = "crosshair";
      } else {
        mapContainerRef.current.style.cursor = "";
      }
    }
  }, [isDropPinMode, onMapClickForPin]);

  const [isDarkModeTiles, setIsDarkModeTiles] = useState<boolean>(true);
  const [hoveredCoords, setHoveredCoords] = useState<{ lat: number; lng: number } | null>(null);

  // Initialize Map
  useEffect(() => {
    if (!mapContainerRef.current || mapInstanceRef.current) return;

    const initialLat = Number(process.env.NEXT_PUBLIC_MAP_DEFAULT_LAT) || 37.7749;
    const initialLng = Number(process.env.NEXT_PUBLIC_MAP_DEFAULT_LNG) || -122.4194;
    const initialZoom = Number(process.env.NEXT_PUBLIC_MAP_DEFAULT_ZOOM) || 14;

    const map = L.map(mapContainerRef.current, {
      center: [initialLat, initialLng],
      zoom: initialZoom,
      zoomControl: false,
      attributionControl: true,
    });

    // Custom OpenStreetMap Tile Layer with Required Visible Attribution
    const tileUrl =
      process.env.NEXT_PUBLIC_MAP_TILE_URL ||
      "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png";
    const tileAttribution =
      process.env.NEXT_PUBLIC_MAP_ATTRIBUTION ||
      '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a> contributors';

    const osmLayer = L.tileLayer(tileUrl, {
      maxZoom: 19,
      attribution: tileAttribution,
      className: isDarkModeTiles ? "dark-tiles" : "",
    });
    osmLayer.addTo(map);

    // Zoom controls at bottom right
    L.control.zoom({ position: "bottomright" }).addTo(map);

    // Track mouse coordinate hover
    map.on("mousemove", (e: L.LeafletMouseEvent) => {
      setHoveredCoords({ lat: e.latlng.lat, lng: e.latlng.lng });
    });

    // Handle click to drop pin
    map.on("click", (e: L.LeafletMouseEvent) => {
      if (isDropPinModeRef.current && onMapClickForPinRef.current) {
        onMapClickForPinRef.current({ lat: e.latlng.lat, lng: e.latlng.lng });
      }
    });

    // Layer groups for zones and pins
    const zoneGroup = L.layerGroup().addTo(map);
    polygonsGroupRef.current = zoneGroup;

    const pinsGroup = L.layerGroup().addTo(map);
    pinsGroupRef.current = pinsGroup;

    const peersGroup = L.layerGroup().addTo(map);
    peersGroupRef.current = peersGroup;

    mapInstanceRef.current = map;
    if (typeof window !== "undefined") {
      (window as any).__nexusMap = map;
    }

    return () => {
      if (typeof window !== "undefined") {
        delete (window as any).__nexusMap;
      }
      map.remove();
      mapInstanceRef.current = null;
    };
  }, []);

  // Update Tile Mode Class
  useEffect(() => {
    if (!mapInstanceRef.current) return;
    const container = mapContainerRef.current;
    if (container) {
      if (isDarkModeTiles) {
        container.classList.add("dark-tiles");
      } else {
        container.classList.remove("dark-tiles");
      }
    }
  }, [isDarkModeTiles]);

  // Initial map centering on actual GPS position — executed ONLY ONCE when coordinates first arrive
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map || !userCoords) return;

    if (!hasCenteredInitiallyRef.current) {
      hasCenteredInitiallyRef.current = true;
      map.setView([userCoords.lat, userCoords.lng], 16);
    }
  }, [userCoords]);

  // Explicit recentering via "My Location" or recenterTrigger — does NOT interrupt regular watchPosition movement
  useEffect(() => {
    if (recenterTrigger > 0 && mapInstanceRef.current && userCoords) {
      mapInstanceRef.current.flyTo([userCoords.lat, userCoords.lng], 16, { duration: 1.2 });
    }
  }, [recenterTrigger, userCoords]);

  // Render Geofence Zones
  useEffect(() => {
    const map = mapInstanceRef.current;
    const zoneGroup = polygonsGroupRef.current;
    if (!map || !zoneGroup) return;

    zoneGroup.clearLayers();

    campusZones.forEach((zone) => {
      const isSelected = selectedZone?.id === zone.id;
      const polygon = L.polygon(zone.polygon, {
        color: zone.color,
        weight: isSelected ? 3 : 1.5,
        fillColor: zone.color,
        fillOpacity: isSelected ? 0.35 : 0.15,
        dashArray: isSelected ? "" : "4, 4",
      });

      // Zone Popup and Interactivity
      polygon.bindTooltip(
        `<div class="font-sans text-xs">
          <strong style="color: ${zone.color}">${zone.name}</strong><br/>
          <span>${zone.activeUsers} Active Nodes</span>
        </div>`,
        { permanent: false, direction: "center", className: "glass-panel text-slate-100" }
      );

      polygon.on("click", () => {
        onSelectZone(zone);
        map.flyTo(zone.center, 16, { duration: 1.2 });
      });

      polygon.addTo(zoneGroup);
    });
  }, [campusZones, selectedZone, onSelectZone]);

  // Render Pins and Meetups
  useEffect(() => {
    const map = mapInstanceRef.current;
    const pinsGroup = pinsGroupRef.current;
    if (!map || !pinsGroup) return;

    pinsGroup.clearLayers();

    pins.forEach((pin) => {
      const getCategoryColor = (cat: string) => {
        switch (cat) {
          case "MEETUP": return "#8b5cf6";
          case "EVENT": return "#3b82f6";
          case "FOOD": return "#f97316";
          case "LOST_AND_FOUND": return "#f59e0b";
          case "EMERGENCY": return "#ef4444";
          case "CAMPUS_ISSUE": return "#eab308";
          case "GENERAL_ANNOUNCEMENT":
          default: return "#06b6d4";
        }
      };

      const getCategoryEmoji = (cat: string) => {
        switch (cat) {
          case "MEETUP": return "👥";
          case "EVENT": return "📅";
          case "FOOD": return "🍕";
          case "LOST_AND_FOUND": return "🔍";
          case "EMERGENCY": return "🚨";
          case "CAMPUS_ISSUE": return "⚠️";
          case "GENERAL_ANNOUNCEMENT":
          default: return "📢";
        }
      };

      const color = getCategoryColor(pin.category);
      const emoji = getCategoryEmoji(pin.category);

      const pinIcon = L.divIcon({
        className: "custom-pin-marker",
        html: `
          <div style="
            position: relative;
            display: flex;
            align-items: center;
            justify-content: center;
            width: 32px;
            height: 32px;
            border-radius: 9999px;
            background: #090d16;
            border: 2px solid ${color};
            box-shadow: 0 0 10px ${color}80;
            font-size: 15px;
            cursor: pointer;
            transition: transform 0.2s;
          ">
            <span>${emoji}</span>
            <span style="
              position: absolute;
              bottom: -2px;
              right: -2px;
              width: 9px;
              height: 9px;
              border-radius: 9999px;
              background: ${color};
              border: 2px solid #090d16;
            "></span>
          </div>
        `,
        iconSize: [32, 32],
        iconAnchor: [16, 16],
        popupAnchor: [0, -18],
      });

      const marker = L.marker([pin.latitude, pin.longitude], { icon: pinIcon });

      const distText = pin.distanceMeters !== undefined && pin.distanceMeters !== null
        ? (pin.distanceMeters < 1000 ? `${Math.round(pin.distanceMeters)}m away` : `${(pin.distanceMeters / 1000).toFixed(1)}km away`)
        : "";

      const expiresText = new Date(pin.expiresAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

      let meetupHtml = "";
      if (pin.meetup) {
        const m = pin.meetup;
        const startFormatted = new Date(m.startTime).toLocaleString([], {
          month: "short",
          day: "numeric",
          hour: "2-digit",
          minute: "2-digit",
        });
        const capacityText = m.capacity ? `${m.currentParticipants} / ${m.capacity}` : `${m.currentParticipants} attending`;
        const joinBtnText = m.isJoined ? "Leave Meetup" : (m.isFull ? "Meetup Full" : "Join Meetup");
        const joinBtnClass = m.isJoined
          ? "leave-meetup-btn bg-rose-950 hover:bg-rose-900 text-rose-300 border border-rose-600/50"
          : (m.isFull ? "bg-slate-800 text-slate-500 cursor-not-allowed" : "join-meetup-btn bg-purple-600 hover:bg-purple-500 text-white");

        meetupHtml = `
          <div style="background: rgba(139, 92, 246, 0.12); border: 1px solid rgba(139, 92, 246, 0.35); border-radius: 8px; padding: 8px; margin-top: 8px; margin-bottom: 6px;">
            <div style="display: flex; justify-content: space-between; font-size: 10px; color: #cbd5e1; margin-bottom: 4px;">
              <span>🕒 ${startFormatted}</span>
              <span style="font-family: monospace; color: ${m.isFull ? '#f43f5e' : '#a855f7'}; font-weight: bold;">👥 ${capacityText}</span>
            </div>
            <button class="${joinBtnClass}" style="width: 100%; padding: 5px 8px; border-radius: 6px; font-size: 11px; font-weight: bold; cursor: pointer; transition: all 0.2s;" ${m.isFull && !m.isJoined ? 'disabled' : ''}>
              ${joinBtnText}
            </button>
          </div>
        `;
      }

      let deleteHtml = "";
      if (pin.isCreator) {
        deleteHtml = `
          <button class="delete-pin-btn" style="margin-top: 6px; width: 100%; padding: 4px 6px; border-radius: 6px; background: rgba(244, 63, 94, 0.15); border: 1px solid rgba(244, 63, 94, 0.4); color: #f43f5e; font-size: 10px; font-weight: 600; cursor: pointer;">
            Delete My Pin
          </button>
        `;
      }

      const popupContent = `
        <div style="font-family: system-ui, sans-serif; color: #f1f5f9; min-width: 220px; max-width: 280px; padding: 4px;">
          <div style="display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 6px;">
            <span style="font-size: 10px; font-weight: 800; text-transform: uppercase; background: ${color}20; color: ${color}; border: 1px solid ${color}40; border-radius: 4px; padding: 2px 6px;">
              ${pin.category.replace('_', ' ')}
            </span>
            <span style="font-size: 10px; font-family: monospace; color: #94a3b8;">${distText}</span>
          </div>
          <h4 style="font-size: 13px; font-weight: 700; color: #ffffff; margin: 0 0 4px 0;">${pin.title}</h4>
          <p style="font-size: 11px; color: #cbd5e1; margin: 0 0 6px 0; line-height: 1.4;">${pin.description}</p>
          <div style="display: flex; justify-content: space-between; font-size: 10px; color: #64748b; font-family: monospace; border-top: 1px solid #1e293b; padding-top: 4px;">
            <span>By: ${pin.creatorName}</span>
            <span>Expires: ${expiresText}</span>
          </div>
          ${meetupHtml}
          ${deleteHtml}
        </div>
      `;

      marker.bindPopup(popupContent, {
        className: "custom-leaflet-popup",
        closeButton: true,
      });

      marker.on("popupopen", (e) => {
        const popupElement = e.popup.getElement();
        if (!popupElement) return;

        const joinBtn = popupElement.querySelector(".join-meetup-btn");
        if (joinBtn) {
          joinBtn.addEventListener("click", () => onJoinMeetup?.(pin.id));
        }

        const leaveBtn = popupElement.querySelector(".leave-meetup-btn");
        if (leaveBtn) {
          leaveBtn.addEventListener("click", () => onLeaveMeetup?.(pin.id));
        }

        const deleteBtn = popupElement.querySelector(".delete-pin-btn");
        if (deleteBtn) {
          deleteBtn.addEventListener("click", () => onDeletePin?.(pin.id));
        }
      });

      marker.addTo(pinsGroup);
    });
  }, [pins, onJoinMeetup, onLeaveMeetup, onDeletePin]);

  // Render User Coordinates Marker — updates marker position on movement WITHOUT recentering map
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;

    if (!userCoords) {
      if (userMarkerRef.current) {
        userMarkerRef.current.remove();
        userMarkerRef.current = null;
      }
      if (accuracyCircleRef.current) {
        accuracyCircleRef.current.remove();
        accuracyCircleRef.current = null;
      }
      return;
    }

    const markerColor = isGhostMode ? "#a855f7" : "#00f0ff";
    const haloColor = isGhostMode ? "rgba(168, 85, 247, 0.25)" : "rgba(0, 240, 255, 0.25)";
    const accuracyRadius = userCoords.accuracy
      ? Math.min(Math.max(userCoords.accuracy, 15), 100)
      : (isGhostMode ? 85 : 30);

    if (!userMarkerRef.current) {
      // User Pulse Accuracy Halo
      const accuracyCircle = L.circle([userCoords.lat, userCoords.lng], {
        radius: accuracyRadius,
        fillColor: haloColor,
        fillOpacity: 0.4,
        color: markerColor,
        weight: 1,
        dashArray: isGhostMode ? "5, 5" : "",
      }).addTo(map);
      accuracyCircleRef.current = accuracyCircle;

      // User Core Dot
      const marker = L.circleMarker([userCoords.lat, userCoords.lng], {
        radius: 8,
        fillColor: markerColor,
        color: "#ffffff",
        weight: 2,
        fillOpacity: 0.95,
      }).addTo(map);

      marker.bindPopup(
        `<div class="p-2 font-sans text-xs space-y-1">
          <strong class="${isGhostMode ? 'text-purple-400' : 'text-cyan-400'} flex items-center gap-1">
            <span>${isGhostMode ? '🛡️' : '📍'}</span>
            <span>${isGhostMode ? 'Local Device Only (Ghost Mode Active)' : 'My Browser GPS Position'}</span>
          </strong>
          <p class="text-[11px] text-slate-300">
            ${isGhostMode 
              ? 'Your coordinates are <strong>completely hidden</strong> from peers, campus radars, and live telemetry.' 
              : 'Protected with <strong>Server-Side Gaussian Fuzzing (~100m)</strong>. Peers observe only obfuscated coordinates.'}
          </p>
          <div class="text-[10px] text-slate-400 font-mono">
            Lat: ${userCoords.lat.toFixed(5)}, Lng: ${userCoords.lng.toFixed(5)}
            ${userCoords.accuracy ? `<br/>Accuracy: &plusmn;${Math.round(userCoords.accuracy)}m` : ''}
          </div>
        </div>`
      );

      userMarkerRef.current = marker;
    } else {
      // Continuous updates from watchPosition — update marker and accuracy circle without altering map center
      userMarkerRef.current.setLatLng([userCoords.lat, userCoords.lng]);
      userMarkerRef.current.setStyle({
        fillColor: markerColor,
      });

      if (accuracyCircleRef.current) {
        accuracyCircleRef.current.setLatLng([userCoords.lat, userCoords.lng]);
        accuracyCircleRef.current.setRadius(accuracyRadius);
        accuracyCircleRef.current.setStyle({
          color: markerColor,
          fillColor: haloColor,
          dashArray: isGhostMode ? "5, 5" : "",
        });
      }
    }
  }, [userCoords, isGhostMode]);

  // Render Active Zone Peers (Fuzzed Coordinates & Anti-Averaging Ring)
  useEffect(() => {
    const peersGroup = peersGroupRef.current;
    if (!peersGroup) return;

    peersGroup.clearLayers();

    peerLocations.forEach((peer) => {
      const fuzzRadius = peer.fuzzingRadiusMeters || 100;

      // 1. Privacy Halo Circle
      L.circle([peer.latitude, peer.longitude], {
        radius: fuzzRadius,
        fillColor: "rgba(6, 182, 212, 0.12)",
        fillOpacity: 0.35,
        color: "#06b6d4",
        weight: 1,
        dashArray: "4, 4",
      }).addTo(peersGroup);

      // 2. Peer Radar Dot
      const peerMarker = L.circleMarker([peer.latitude, peer.longitude], {
        radius: 6,
        fillColor: "#06b6d4",
        color: "#ffffff",
        weight: 1.5,
        fillOpacity: 0.9,
      }).addTo(peersGroup);

      peerMarker.bindPopup(
        `<div class="p-2 font-sans text-xs space-y-1">
          <div class="flex items-center gap-1.5 font-bold text-cyan-400">
            <span>👤</span>
            <span>${peer.displayName}</span>
          </div>
          <div class="text-[11px] text-slate-300">
            🛡️ Server Fuzzed (~${fuzzRadius}m radius)
          </div>
          <div class="text-[10px] text-slate-400">
            Zone: ${peer.zoneName}
          </div>
          <div class="text-[9px] text-cyan-500/80 font-mono pt-0.5">
            Anti-Averaging Protection Active
          </div>
        </div>`
      );
    });
  }, [peerLocations]);

  // Center on active campus perimeter or default view
  const handleResetView = () => {
    if (!mapInstanceRef.current) return;
    if (selectedZone) {
      mapInstanceRef.current.flyTo(selectedZone.center, 16, { duration: 1.2 });
    } else {
      mapInstanceRef.current.flyTo([37.7749, -122.4194], 14, { duration: 1.2 });
    }
  };

  return (
    <div className="relative w-full h-[calc(100vh-60px)] mt-[60px] overflow-hidden select-none">
      {/* The Leaflet Map Container */}
      <div
        id="nexus-leaflet-map"
        ref={mapContainerRef}
        className={`w-full h-full ${isDarkModeTiles ? "dark-tiles" : ""}`}
      />

      {/* Drop Pin Mode Active Banner */}
      {isDropPinMode && (
        <div className="absolute top-4 left-1/2 -translate-x-1/2 z-30 flex items-center gap-2 px-4 py-2 rounded-xl bg-purple-950/95 border border-purple-500/60 shadow-2xl backdrop-blur-xl text-purple-200 text-xs font-bold animate-pulse">
          <Crosshair className="w-4 h-4 text-purple-400" />
          <span>Click anywhere on the map to drop your global pin</span>
        </div>
      )}

      {/* Location Error / Permission Notification Banner */}
      {locationError && (
        <div
          id="location-error-alert"
          className="absolute top-4 left-1/2 -translate-x-1/2 z-30 max-w-lg w-[90%] px-4 py-2.5 rounded-xl bg-slate-900/95 border border-rose-500/60 shadow-2xl backdrop-blur-xl text-xs flex items-center justify-between gap-3 text-rose-200"
        >
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0" />
            <span>{locationError}</span>
          </div>
          <button
            type="button"
            onClick={onRequestLocation}
            className="px-2.5 py-1 rounded-lg bg-rose-900/60 hover:bg-rose-800/80 border border-rose-400/40 text-white font-medium shrink-0 text-[11px] transition-colors"
          >
            Retry
          </button>
        </div>
      )}

      {/* Floating Control HUD (Top-Left): Map Actions & "My Location" */}
      <div className="absolute top-4 left-4 z-20 flex flex-col gap-2">
        <div className="glass-panel-glow rounded-xl p-1.5 flex items-center gap-1.5">
          {/* Requirement 10: "My Location" Button */}
          <button
            id="my-location-btn"
            type="button"
            onClick={() => {
              if (userCoords && mapInstanceRef.current) {
                mapInstanceRef.current.flyTo([userCoords.lat, userCoords.lng], 16, { duration: 1.2 });
              }
              onRecenterLocation();
            }}
            className="flex items-center gap-1.5 px-3 py-2 rounded-lg bg-slate-900/90 text-cyan-300 hover:bg-cyan-950 hover:text-cyan-200 border border-cyan-500/40 text-xs font-semibold shadow-md transition-all active:scale-95"
            title="My Location: Center map on your live GPS coordinates"
          >
            <Crosshair
              className={`w-4 h-4 text-cyan-400 ${
                locationStatus === "getting_location" ? "animate-spin" : ""
              }`}
            />
            <span>My Location</span>
          </button>

          {/* Recenter Campus Zone View */}
          <button
            type="button"
            onClick={handleResetView}
            className="p-2 rounded-lg bg-slate-900/90 text-slate-300 hover:text-white hover:bg-slate-800 border border-slate-700/60 transition-colors"
            title="Focus Active Campus Zone"
          >
            <Compass className="w-4 h-4 text-emerald-400" />
          </button>

          {/* Toggle Map Dark / OSM Original Style */}
          <button
            type="button"
            onClick={() => setIsDarkModeTiles(!isDarkModeTiles)}
            className="p-2 rounded-lg bg-slate-900/90 text-slate-300 hover:text-white hover:bg-slate-800 border border-slate-700/60 transition-colors"
            title={isDarkModeTiles ? "Switch to standard OSM tiles" : "Switch to dark cartography mode"}
          >
            <Layers className="w-4 h-4 text-teal-400" />
          </button>
        </div>
      </div>

      {/* Requirement 11: Visible Location Status Badge */}
      <div className="absolute top-4 right-16 z-20 flex items-center">
        <div
          id="location-status-badge"
          className={`px-3 py-1.5 rounded-xl border backdrop-blur-md shadow-lg flex items-center gap-2 text-xs font-semibold ${
            locationStatus === "location_available"
              ? "bg-emerald-950/80 border-emerald-500/50 text-emerald-300 shadow-[0_0_12px_rgba(16,185,129,0.2)]"
              : locationStatus === "getting_location"
              ? "bg-cyan-950/80 border-cyan-500/50 text-cyan-300 shadow-[0_0_12px_rgba(6,182,212,0.2)]"
              : locationStatus === "permission_denied"
              ? "bg-rose-950/80 border-rose-500/50 text-rose-300 shadow-[0_0_12px_rgba(244,63,94,0.2)]"
              : "bg-amber-950/80 border-amber-500/50 text-amber-300 shadow-[0_0_12px_rgba(245,158,11,0.2)]"
          }`}
        >
          {locationStatus === "location_available" && (
            <>
              <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
              <span>Location available</span>
            </>
          )}
          {locationStatus === "getting_location" && (
            <>
              <span className="w-2 h-2 rounded-full bg-cyan-400 animate-ping" />
              <span>Getting location</span>
            </>
          )}
          {locationStatus === "permission_denied" && (
            <>
              <AlertTriangle className="w-3.5 h-3.5 text-rose-400" />
              <span>Permission denied</span>
            </>
          )}
          {locationStatus === "location_unavailable" && (
            <>
              <AlertTriangle className="w-3.5 h-3.5 text-amber-400" />
              <span>Location unavailable</span>
            </>
          )}
        </div>
      </div>

      {/* Bottom Live Cursor Telemetry Strip */}
      <div className="absolute bottom-3 left-4 z-20 hidden md:flex items-center gap-3 px-3 py-1.5 rounded-lg glass-panel text-[11px] font-mono text-slate-400">
        <div className="flex items-center gap-1.5">
          <span className="w-2 h-2 rounded-full bg-cyan-400 animate-pulse" />
          <span>CURSOR:</span>
          <span className="text-cyan-300">
            {hoveredCoords
              ? `${hoveredCoords.lat.toFixed(5)}°, ${hoveredCoords.lng.toFixed(5)}°`
              : "Scanning surface..."}
          </span>
        </div>
        <div className="h-3 w-px bg-slate-700" />
        <div className="flex items-center gap-1 text-slate-400">
          <Zap className="w-3 h-3 text-emerald-400" />
          <span>POSTGIS SRID:4326</span>
        </div>
      </div>
    </div>
  );
};
