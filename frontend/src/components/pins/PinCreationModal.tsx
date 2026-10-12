"use client";

import React, { useState } from "react";
import { PinCategory, PinResponse } from "@/types";
import { useAuth } from "@/context/AuthContext";
import {
  MapPin,
  X,
  Users,
  Calendar,
  Utensils,
  AlertTriangle,
  HelpCircle,
  Wrench,
  Radio,
  Clock,
  Sparkles,
  AlertCircle,
  RefreshCw,
} from "lucide-react";

interface PinCreationModalProps {
  isOpen: boolean;
  onClose: () => void;
  coords: { lat: number; lng: number } | null;
  onPinCreated: (pin: PinResponse) => void;
}

const CATEGORIES: {
  value: PinCategory;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  color: string;
  desc: string;
}[] = [
  {
    value: "MEETUP",
    label: "Meetup",
    icon: Users,
    color: "#8b5cf6",
    desc: "Gathering, study group, or collaborative session",
  },
  {
    value: "EVENT",
    label: "Campus Event",
    icon: Calendar,
    color: "#3b82f6",
    desc: "Workshops, club fairs, keynotes, or sports",
  },
  {
    value: "FOOD",
    label: "Food Alert",
    icon: Utensils,
    color: "#f97316",
    desc: "Free food, surplus dining catering, or food truck",
  },
  {
    value: "LOST_AND_FOUND",
    label: "Lost & Found",
    icon: HelpCircle,
    color: "#f59e0b",
    desc: "Misplaced items, recovered tech, or textbooks",
  },
  {
    value: "EMERGENCY",
    label: "Emergency",
    icon: AlertTriangle,
    color: "#ef4444",
    desc: "Urgent security or medical alert",
  },
  {
    value: "CAMPUS_ISSUE",
    label: "Campus Issue",
    icon: Wrench,
    color: "#eab308",
    desc: "Broken equipment, facility hazard, or elevator outage",
  },
  {
    value: "GENERAL_ANNOUNCEMENT",
    label: "Announcement",
    icon: Radio,
    color: "#06b6d4",
    desc: "Public bulletin, notice, or community shoutout",
  },
];

export const PinCreationModal: React.FC<PinCreationModalProps> = ({
  isOpen,
  onClose,
  coords,
  onPinCreated,
}) => {
  const { isAuthenticated } = useAuth();
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [category, setCategory] = useState<PinCategory>("MEETUP");
  const [durationHours, setDurationHours] = useState<number>(24);

  // Meetup specific fields
  const [startTime, setStartTime] = useState<string>("");
  const [endTime, setEndTime] = useState<string>("");
  const [capacity, setCapacity] = useState<string>("");

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!coords) {
      setError("Please select a location on the map first.");
      return;
    }
    if (!title.trim() || title.length < 3) {
      setError("Title must be at least 3 characters long.");
      return;
    }
    if (!description.trim()) {
      setError("Description is required.");
      return;
    }

    setIsSubmitting(true);
    setError(null);

    const payload: any = {
      title: title.trim(),
      description: description.trim(),
      category,
      latitude: coords.lat,
      longitude: coords.lng,
      durationHours,
    };

    if (category === "MEETUP") {
      const now = new Date();
      const defaultStart = new Date(now.getTime() + 60 * 60 * 1000).toISOString();
      payload.meetupDetails = {
        startTime: startTime ? new Date(startTime).toISOString() : defaultStart,
        endTime: endTime ? new Date(endTime).toISOString() : null,
        capacity: capacity ? parseInt(capacity, 10) : null,
      };
    }

    try {
      const res = await fetch("/api/pins", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify(payload),
      });

      if (res.ok) {
        const createdPin: PinResponse = await res.json();
        onPinCreated(createdPin);
        onClose();
        // Reset form
        setTitle("");
        setDescription("");
        setCategory("MEETUP");
        setDurationHours(24);
        setStartTime("");
        setEndTime("");
        setCapacity("");
      } else {
        const errData = await res.json().catch(() => ({}));
        setError(errData.message || "Failed to create pin.");
      }
    } catch (err: any) {
      setError("Network error: " + (err.message || "Could not reach server"));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-md animate-fadeIn">
      <div className="relative w-full max-w-lg rounded-2xl bg-slate-950/95 border border-slate-800 shadow-2xl overflow-hidden font-sans">
        {/* Header */}
        <div className="flex items-center justify-between p-4 bg-slate-900/90 border-b border-slate-800">
          <div className="flex items-center gap-2">
            <div className="p-2 rounded-xl bg-cyan-950/60 border border-cyan-500/40 text-cyan-400">
              <MapPin className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-slate-100">Drop Global Pin</h3>
              <p className="text-[11px] text-slate-400 font-mono">
                {coords
                  ? `${coords.lat.toFixed(5)}°, ${coords.lng.toFixed(5)}°`
                  : "Target location"}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-4 space-y-4 max-h-[calc(85vh-80px)] overflow-y-auto">
          {error && (
            <div className="p-3 rounded-xl bg-rose-950/60 border border-rose-500/40 text-rose-300 text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0 text-rose-400" />
              <span>{error}</span>
            </div>
          )}

          {/* Category Selector */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300">Category</label>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
              {CATEGORIES.map((cat) => {
                const IconComponent = cat.icon;
                const isSelected = category === cat.value;
                return (
                  <button
                    key={cat.value}
                    type="button"
                    onClick={() => setCategory(cat.value)}
                    className={`flex items-center gap-2 p-2 rounded-xl border text-left transition-all ${
                      isSelected
                        ? "bg-slate-800 border-cyan-500 shadow-md ring-1 ring-cyan-500/40 text-white"
                        : "bg-slate-900/60 border-slate-800 text-slate-400 hover:text-slate-200 hover:bg-slate-900"
                    }`}
                  >
                    <span
                      className="p-1 rounded-lg shrink-0"
                      style={{ backgroundColor: `${cat.color}25`, color: cat.color }}
                    >
                      <IconComponent className="w-3.5 h-3.5" />
                    </span>
                    <span className="text-[11px] font-semibold truncate">{cat.label}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Title */}
          <div className="space-y-1">
            <label className="text-xs font-semibold text-slate-300">Title</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. Next.js 14 Hackathon Jam"
              maxLength={100}
              required
              className="w-full bg-slate-900 border border-slate-800 focus:border-cyan-500 rounded-xl px-3 py-2 text-xs text-slate-100 placeholder-slate-500 outline-none transition-all"
            />
          </div>

          {/* Description */}
          <div className="space-y-1">
            <label className="text-xs font-semibold text-slate-300">Description</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Provide context, room number, or meetup details..."
              maxLength={2000}
              rows={3}
              required
              className="w-full bg-slate-900 border border-slate-800 focus:border-cyan-500 rounded-xl px-3 py-2 text-xs text-slate-100 placeholder-slate-500 outline-none transition-all resize-none"
            />
          </div>

          {/* Expiration Duration */}
          <div className="space-y-1">
            <label className="text-xs font-semibold text-slate-300 flex items-center gap-1.5">
              <Clock className="w-3.5 h-3.5 text-cyan-400" />
              <span>Decay / Expiration Window</span>
            </label>
            <select
              value={durationHours}
              onChange={(e) => setDurationHours(parseInt(e.target.value, 10))}
              className="w-full bg-slate-900 border border-slate-800 focus:border-cyan-500 rounded-xl px-3 py-2 text-xs text-slate-200 outline-none transition-all"
            >
              <option value={2}>2 Hours (Flash alert / Food)</option>
              <option value={6}>6 Hours (Half day)</option>
              <option value={12}>12 Hours</option>
              <option value={24}>24 Hours (1 Day — Standard)</option>
              <option value={72}>3 Days (Weekend event)</option>
              <option value={168}>7 Days (1 Week)</option>
              <option value={720}>30 Days (Maximum limit)</option>
            </select>
          </div>

          {/* Meetup Specific Section */}
          {category === "MEETUP" && (
            <div className="p-3 rounded-xl bg-purple-950/20 border border-purple-500/30 space-y-3">
              <div className="flex items-center gap-2 text-xs font-bold text-purple-300">
                <Users className="w-3.5 h-3.5" />
                <span>Meetup Session Logistics</span>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
                <div>
                  <label className="text-[11px] text-slate-400">Start Time</label>
                  <input
                    type="datetime-local"
                    value={startTime}
                    onChange={(e) => setStartTime(e.target.value)}
                    className="w-full mt-1 bg-slate-900 border border-slate-800 rounded-lg px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-purple-500"
                  />
                </div>
                <div>
                  <label className="text-[11px] text-slate-400">End Time (Optional)</label>
                  <input
                    type="datetime-local"
                    value={endTime}
                    onChange={(e) => setEndTime(e.target.value)}
                    className="w-full mt-1 bg-slate-900 border border-slate-800 rounded-lg px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-purple-500"
                  />
                </div>
              </div>

              <div>
                <label className="text-[11px] text-slate-400">Max Capacity (Leave blank for unlimited)</label>
                <input
                  type="number"
                  min={1}
                  max={10000}
                  value={capacity}
                  onChange={(e) => setCapacity(e.target.value)}
                  placeholder="e.g. 15 participants"
                  className="w-full mt-1 bg-slate-900 border border-slate-800 rounded-lg px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-purple-500"
                />
              </div>
            </div>
          )}

          {/* Footer Actions */}
          <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-800">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-xl text-xs font-semibold text-slate-400 hover:text-white hover:bg-slate-900 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting || !isAuthenticated}
              className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-cyan-600 hover:bg-cyan-500 active:scale-95 disabled:bg-slate-800 disabled:text-slate-600 text-white text-xs font-bold transition-all shadow-lg"
            >
              {isSubmitting ? (
                <>
                  <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                  <span>Dropping Pin...</span>
                </>
              ) : (
                <>
                  <Sparkles className="w-3.5 h-3.5" />
                  <span>Deploy Pin</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
