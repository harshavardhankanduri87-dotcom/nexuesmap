"use client";

import React, { useState } from "react";
import { PinCategory } from "@/types";
import {
  MapPin,
  Filter,
  Plus,
  Compass,
  Layers,
  ChevronDown,
  ChevronUp,
  Sparkles,
  Users,
  Calendar,
  Utensils,
  HelpCircle,
  AlertTriangle,
  Wrench,
  Radio,
} from "lucide-react";

interface PinFilterHUDProps {
  selectedCategory: PinCategory | "ALL";
  onSelectCategory: (category: PinCategory | "ALL") => void;
  radiusMeters: number;
  onChangeRadius: (radius: number) => void;
  isDropPinMode: boolean;
  onToggleDropPinMode: () => void;
  pinCount: number;
}

const CATEGORY_ITEMS: {
  value: PinCategory | "ALL";
  label: string;
  icon: React.ComponentType<{ className?: string; style?: React.CSSProperties }>;
  color: string;
}[] = [
  { value: "ALL", label: "All Pins", icon: Layers, color: "#00f0ff" },
  { value: "MEETUP", label: "Meetups", icon: Users, color: "#8b5cf6" },
  { value: "EVENT", label: "Events", icon: Calendar, color: "#3b82f6" },
  { value: "FOOD", label: "Food", icon: Utensils, color: "#f97316" },
  { value: "LOST_AND_FOUND", label: "Lost & Found", icon: HelpCircle, color: "#f59e0b" },
  { value: "EMERGENCY", label: "Emergency", icon: AlertTriangle, color: "#ef4444" },
  { value: "CAMPUS_ISSUE", label: "Issues", icon: Wrench, color: "#eab308" },
  { value: "GENERAL_ANNOUNCEMENT", label: "Announcements", icon: Radio, color: "#06b6d4" },
];

const RADIUS_OPTIONS = [
  { label: "250m", value: 250 },
  { label: "500m", value: 500 },
  { label: "1km", value: 1000 },
  { label: "5km", value: 5000 },
  { label: "10km", value: 10000 },
  { label: "25km", value: 25000 },
];

export const PinFilterHUD: React.FC<PinFilterHUDProps> = ({
  selectedCategory,
  onSelectCategory,
  radiusMeters,
  onChangeRadius,
  isDropPinMode,
  onToggleDropPinMode,
  pinCount,
}) => {
  const [isOpen, setIsOpen] = useState(true);

  return (
    <div className="absolute top-20 left-4 z-20 transition-all font-sans">
      <div className="glass-panel-glow rounded-2xl overflow-hidden shadow-2xl border border-slate-700/80 bg-slate-950/90 backdrop-blur-xl w-72 sm:w-80">
        {/* Header */}
        <div className="flex items-center justify-between p-3 bg-slate-900/90 border-b border-slate-800">
          <div className="flex items-center gap-2">
            <MapPin className="w-4 h-4 text-cyan-400" />
            <span className="text-xs font-bold tracking-wider text-slate-200 uppercase">
              Global Pins &amp; Radar
            </span>
          </div>

          <div className="flex items-center gap-2">
            <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-cyan-950/80 text-cyan-300 border border-cyan-800/50">
              {pinCount} Pins
            </span>
            <button
              type="button"
              onClick={() => setIsOpen(!isOpen)}
              className="p-1 rounded-lg text-slate-400 hover:text-white transition-colors"
            >
              {isOpen ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
            </button>
          </div>
        </div>

        {isOpen && (
          <div className="p-3 space-y-3">
            {/* Drop Pin Action Trigger */}
            <button
              type="button"
              onClick={onToggleDropPinMode}
              className={`w-full flex items-center justify-center gap-2 py-2 px-3 rounded-xl text-xs font-bold transition-all shadow-md ${
                isDropPinMode
                  ? "bg-purple-600 text-white shadow-[0_0_15px_rgba(168,85,247,0.5)] animate-pulse border border-purple-400"
                  : "bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 text-white border border-cyan-400/40"
              }`}
            >
              <Plus className="w-4 h-4" />
              <span>{isDropPinMode ? "Click Map to Place Pin..." : "Deploy Global Pin"}</span>
            </button>

            {/* Radius Proximity Filter */}
            <div className="space-y-1.5">
              <div className="flex items-center justify-between text-[11px] text-slate-400">
                <span className="font-semibold">Discovery Radius</span>
                <span className="font-mono text-cyan-300">
                  {radiusMeters >= 1000 ? `${radiusMeters / 1000}km` : `${radiusMeters}m`}
                </span>
              </div>
              <div className="grid grid-cols-6 gap-1 bg-slate-900/80 p-1 rounded-xl border border-slate-800">
                {RADIUS_OPTIONS.map((opt) => {
                  const isSelected = radiusMeters === opt.value;
                  return (
                    <button
                      key={opt.value}
                      type="button"
                      onClick={() => onChangeRadius(opt.value)}
                      className={`py-1 text-[10px] font-mono rounded-lg transition-all text-center ${
                        isSelected
                          ? "bg-cyan-500/25 text-cyan-300 font-bold border border-cyan-500/50"
                          : "text-slate-400 hover:text-slate-200"
                      }`}
                    >
                      {opt.label}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Category Filter Pills */}
            <div className="space-y-1.5">
              <label className="text-[11px] font-semibold text-slate-400">Category Filter</label>
              <div className="flex flex-wrap gap-1">
                {CATEGORY_ITEMS.map((cat) => {
                  const IconComp = cat.icon;
                  const isSelected = selectedCategory === cat.value;
                  return (
                    <button
                      key={cat.value}
                      type="button"
                      onClick={() => onSelectCategory(cat.value)}
                      className={`flex items-center gap-1 px-2 py-1 rounded-lg text-[10px] font-semibold transition-all border ${
                        isSelected
                          ? "bg-slate-800 text-white border-cyan-500 shadow-sm ring-1 ring-cyan-500/30"
                          : "bg-slate-900/60 text-slate-400 border-slate-800 hover:text-slate-200 hover:bg-slate-900"
                      }`}
                    >
                      <IconComp className="w-3 h-3" style={{ color: cat.color }} />
                      <span>{cat.label}</span>
                    </button>
                  );
                })}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
