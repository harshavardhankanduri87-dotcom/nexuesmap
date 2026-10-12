"use client";

import React, { useEffect, useRef, useState } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { DensityHeatmap, ZoneDensityPoint } from "@/types";
import { Flame, Shield, Layers, Eye, EyeOff, Sliders } from "lucide-react";

interface AdminMapHeatmapProps {
  densityData: DensityHeatmap | null;
  minThreshold: number;
  onChangeThreshold: (threshold: number) => void;
  isLoading: boolean;
}

export const AdminMapHeatmap: React.FC<AdminMapHeatmapProps> = ({
  densityData,
  minThreshold,
  onChangeThreshold,
  isLoading,
}) => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const heatLayerRef = useRef<L.LayerGroup | null>(null);
  const [showHeatmap, setShowHeatmap] = useState<boolean>(true);

  // Initialize Map
  useEffect(() => {
    if (!mapContainerRef.current || mapInstanceRef.current) return;

    const initialLat = Number(process.env.NEXT_PUBLIC_MAP_DEFAULT_LAT) || 37.7749;
    const initialLng = Number(process.env.NEXT_PUBLIC_MAP_DEFAULT_LNG) || -122.4194;

    const map = L.map(mapContainerRef.current, {
      center: [initialLat, initialLng],
      zoom: 14,
      zoomControl: false,
      attributionControl: true,
    });

    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      attribution:
        '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a> contributors',
      className: "dark-tiles",
    }).addTo(map);

    L.control.zoom({ position: "bottomright" }).addTo(map);

    const heatLayer = L.layerGroup().addTo(map);
    heatLayerRef.current = heatLayer;

    mapInstanceRef.current = map;

    return () => {
      map.remove();
      mapInstanceRef.current = null;
    };
  }, []);

  // Update Heatmap Elements
  useEffect(() => {
    const layer = heatLayerRef.current;
    if (!layer) return;

    layer.clearLayers();

    if (!showHeatmap || !densityData || !densityData.densityPoints) return;

    densityData.densityPoints.forEach((point: ZoneDensityPoint) => {
      // 1. Color palette based on intensity/level
      let color = "#10b981"; // Emerald for LOW
      let fillColor = "rgba(16, 185, 129, 0.25)";
      let radius = 60;

      if (point.densityLevel === "MEDIUM") {
        color = "#eab308"; // Amber
        fillColor = "rgba(234, 179, 8, 0.35)";
        radius = 80;
      } else if (point.densityLevel === "HIGH") {
        color = "#f97316"; // Orange
        fillColor = "rgba(249, 115, 22, 0.45)";
        radius = 105;
      } else if (point.densityLevel === "CRITICAL") {
        color = "#ef4444"; // Red
        fillColor = "rgba(239, 68, 68, 0.55)";
        radius = 135;
      }

      // 2. Render Zone Boundary Polygon with Density Fill
      if (point.boundaryCoordinates && point.boundaryCoordinates.length >= 3) {
        const poly = L.polygon(point.boundaryCoordinates, {
          color: color,
          weight: 2,
          fillColor: fillColor,
          fillOpacity: 0.35,
          dashArray: "4, 4",
        }).addTo(layer);

        poly.bindPopup(`
          <div class="p-2 font-sans text-xs space-y-1">
            <div class="font-bold text-slate-100 flex items-center justify-between">
              <span>${point.zoneName}</span>
              <span class="px-1.5 py-0.5 rounded text-[10px] font-mono bg-slate-800 text-cyan-300">${point.densityLevel}</span>
            </div>
            <div class="text-slate-300">Headcount: <strong>${point.userCount}</strong> verified users</div>
            <div class="text-[10px] text-slate-400">Relative Intensity: ${(point.intensity * 100).toFixed(0)}%</div>
            <div class="text-[9px] text-slate-500 font-mono">Protected by ${densityData.minThreshold}-Anonymity Threshold</div>
          </div>
        `);
      }

      // 3. Multi-radial glowing heat pulse at centroid
      L.circle([point.centerLat, point.centerLng], {
        radius: radius * 1.5,
        color: "transparent",
        fillColor: color,
        fillOpacity: 0.15,
      }).addTo(layer);

      L.circle([point.centerLat, point.centerLng], {
        radius: radius,
        color: color,
        weight: 1.5,
        fillColor: color,
        fillOpacity: 0.4,
      }).addTo(layer);

      // 4. Centroid badge marker with count
      const countIcon = L.divIcon({
        className: "heatmap-count-badge",
        html: `
          <div style="background: ${color}; color: #000; font-weight: 800; font-size: 11px; width: 26px; height: 26px; border-radius: 50%; display: flex; align-items: center; justify-content: center; box-shadow: 0 0 10px ${color}; border: 2px solid #fff;">
            ${point.userCount}
          </div>
        `,
        iconSize: [26, 26],
        iconAnchor: [13, 13],
      });

      L.marker([point.centerLat, point.centerLng], { icon: countIcon }).addTo(layer);
    });
  }, [densityData, showHeatmap]);

  return (
    <div className="relative w-full h-[450px] rounded-2xl overflow-hidden border border-slate-800 bg-slate-950 shadow-2xl">
      {/* Leaflet container */}
      <div ref={mapContainerRef} className="w-full h-full" />

      {/* Control HUD Header */}
      <div className="absolute top-4 left-4 z-[500] flex flex-wrap items-center gap-2">
        <div className="flex items-center gap-2 px-3 py-1.5 rounded-xl bg-slate-900/90 backdrop-blur-md border border-slate-800 text-xs text-slate-200 shadow-lg">
          <Flame className="w-4 h-4 text-orange-400" />
          <span className="font-semibold">Geospatial Density Heatmap</span>
        </div>

        <button
          type="button"
          onClick={() => setShowHeatmap((prev) => !prev)}
          className={`flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold backdrop-blur-md border transition-all ${
            showHeatmap
              ? "bg-cyan-950/80 text-cyan-300 border-cyan-500/40 shadow-sm"
              : "bg-slate-900/80 text-slate-400 border-slate-800 hover:text-white"
          }`}
        >
          {showHeatmap ? <Eye className="w-3.5 h-3.5" /> : <EyeOff className="w-3.5 h-3.5" />}
          <span>{showHeatmap ? "Heatmap Active" : "Heatmap Hidden"}</span>
        </button>
      </div>

      {/* Threshold Slider & Privacy Banner */}
      <div className="absolute bottom-4 left-4 right-4 z-[500] flex flex-col sm:flex-row items-center justify-between gap-3 p-3 rounded-xl bg-slate-900/90 backdrop-blur-md border border-slate-800 shadow-xl">
        <div className="flex items-center gap-3 w-full sm:w-auto">
          <div className="flex items-center gap-1.5 text-xs text-slate-300 font-semibold shrink-0">
            <Shield className="w-4 h-4 text-purple-400" />
            <span>k-Anonymity Filter:</span>
          </div>
          <div className="flex items-center gap-2 w-full sm:w-48">
            <input
              type="range"
              min={1}
              max={10}
              step={1}
              value={minThreshold}
              onChange={(e) => onChangeThreshold(parseInt(e.target.value, 10))}
              className="w-full h-1.5 bg-slate-700 rounded-lg appearance-none cursor-pointer accent-purple-400"
            />
            <span className="font-mono text-xs text-purple-300 font-bold bg-purple-950/60 px-2 py-0.5 rounded border border-purple-500/40 shrink-0">
              ≥ {minThreshold}
            </span>
          </div>
        </div>

        <div className="flex items-center gap-3 text-[11px] text-slate-400">
          {densityData && (
            <>
              <span>
                Observed: <strong className="text-slate-200">{densityData.privacyEligibleMembers}</strong> users
              </span>
              <span>•</span>
              <span className={densityData.suppressedAreasCount > 0 ? "text-amber-400" : "text-emerald-400"}>
                Suppressed Areas: <strong>{densityData.suppressedAreasCount}</strong> (under threshold)
              </span>
            </>
          )}
        </div>
      </div>
    </div>
  );
};
