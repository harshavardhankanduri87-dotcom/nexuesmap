"use client";

import React, { useState, useEffect } from "react";
import { PrivacySettings } from "@/types";
import {
  Shield,
  ShieldAlert,
  ShieldCheck,
  Eye,
  EyeOff,
  Navigation,
  Lock,
  Clock,
  Radio,
  Sliders,
  CheckCircle2,
  AlertCircle,
  X,
  RefreshCw,
} from "lucide-react";

interface PrivacySettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  currentSettings: PrivacySettings | null;
  onSaveSettings: (settings: Partial<PrivacySettings>) => Promise<boolean>;
  browserPermissionStatus?: string;
}

export const PrivacySettingsModal: React.FC<PrivacySettingsModalProps> = ({
  isOpen,
  onClose,
  currentSettings,
  onSaveSettings,
  browserPermissionStatus = "granted",
}) => {
  const [ghostMode, setGhostMode] = useState<boolean>(false);
  const [locationSharing, setLocationSharing] = useState<boolean>(true);
  const [fuzzingRadius, setFuzzingRadius] = useState<number>(100);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [saveSuccess, setSaveSuccess] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (currentSettings) {
      setGhostMode(currentSettings.ghostMode);
      setLocationSharing(currentSettings.locationSharingEnabled);
      setFuzzingRadius(currentSettings.fuzzingRadiusMeters || 100);
    }
  }, [currentSettings, isOpen]);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    setErrorMessage(null);
    setSaveSuccess(false);

    try {
      const success = await onSaveSettings({
        ghostMode,
        locationSharingEnabled: locationSharing,
        fuzzingRadiusMeters: fuzzingRadius,
      });

      if (success) {
        setSaveSuccess(true);
        setTimeout(() => {
          setSaveSuccess(false);
          onClose();
        }, 1200);
      } else {
        setErrorMessage("Failed to update privacy settings. Please try again.");
      }
    } catch (err: any) {
      setErrorMessage(err.message || "An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md animate-fade-in">
      <div className="relative w-full max-w-lg bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl shadow-purple-950/20 overflow-hidden">
        {/* Glow accent */}
        <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-purple-500 via-indigo-500 to-cyan-500" />

        {/* Header */}
        <div className="flex items-center justify-between px-6 py-5 border-b border-slate-800/80">
          <div className="flex items-center gap-3">
            <div className={`p-2.5 rounded-xl border ${
              ghostMode
                ? "bg-purple-950/60 border-purple-500/50 text-purple-400 shadow-lg shadow-purple-900/30"
                : "bg-cyan-950/40 border-cyan-500/30 text-cyan-400"
            }`}>
              {ghostMode ? <ShieldAlert className="w-5 h-5 animate-pulse" /> : <ShieldCheck className="w-5 h-5" />}
            </div>
            <div>
              <h2 className="text-base font-bold text-slate-100 flex items-center gap-2">
                Privacy & Ghost Architecture
              </h2>
              <p className="text-xs text-slate-400">
                Server-side location obfuscation and stealth telemetry
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 text-slate-400 hover:text-slate-200 hover:bg-slate-800 rounded-lg transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-5">
          {/* Section 1: Ghost Mode Toggle */}
          <div className={`p-4 rounded-xl border transition-all ${
            ghostMode
              ? "bg-purple-950/30 border-purple-500/40 shadow-inner"
              : "bg-slate-800/40 border-slate-800"
          }`}>
            <div className="flex items-center justify-between">
              <div className="space-y-1 pr-4">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-semibold text-slate-200">Ghost Mode Stealth</span>
                  <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold tracking-wider uppercase border ${
                    ghostMode
                      ? "bg-purple-500/20 text-purple-300 border-purple-500/30 animate-pulse"
                      : "bg-slate-800 text-slate-400 border-slate-700"
                  }`}>
                    {ghostMode ? "ACTIVE" : "STANDBY"}
                  </span>
                </div>
                <p className="text-xs text-slate-400 leading-relaxed">
                  When enabled, your live location is completely stripped from public map beacons, zone rosters, and peer radars. You participate as <span className="text-purple-300 font-mono">Ghost Operator</span>.
                </p>
              </div>
              <button
                type="button"
                onClick={() => setGhostMode((prev) => !prev)}
                className={`relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                  ghostMode ? "bg-purple-600" : "bg-slate-700"
                }`}
              >
                <span
                  className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${
                    ghostMode ? "translate-x-5" : "translate-x-0"
                  }`}
                />
              </button>
            </div>
          </div>

          {/* Section 2: Peer Location Sharing (Only active when Ghost Mode is OFF) */}
          <div className={`p-4 rounded-xl border transition-all ${
            ghostMode ? "opacity-40 pointer-events-none" : "bg-slate-800/40 border-slate-800"
          }`}>
            <div className="flex items-center justify-between">
              <div className="space-y-1 pr-4">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-semibold text-slate-200">Zone Peer Radar Sharing</span>
                  <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold tracking-wider uppercase border ${
                    locationSharing && !ghostMode
                      ? "bg-cyan-500/20 text-cyan-300 border-cyan-500/30"
                      : "bg-slate-800 text-slate-400 border-slate-700"
                  }`}>
                    {locationSharing && !ghostMode ? "ENABLED" : "DISABLED"}
                  </span>
                </div>
                <p className="text-xs text-slate-400 leading-relaxed">
                  Shares a protected, server-fuzzed coordinate with fellow students inside your current campus zone. Raw GPS is never broadcast.
                </p>
              </div>
              <button
                type="button"
                disabled={ghostMode}
                onClick={() => setLocationSharing((prev) => !prev)}
                className={`relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                  locationSharing && !ghostMode ? "bg-cyan-600" : "bg-slate-700"
                }`}
              >
                <span
                  className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${
                    locationSharing && !ghostMode ? "translate-x-5" : "translate-x-0"
                  }`}
                />
              </button>
            </div>
          </div>

          {/* Section 3: Spatial Fuzzing Radius Slider */}
          <div className={`space-y-2 p-4 bg-slate-800/30 border border-slate-800 rounded-xl ${
            ghostMode ? "opacity-50" : ""
          }`}>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Sliders className="w-4 h-4 text-cyan-400" />
                <label className="text-xs font-semibold text-slate-300">
                  Spatial Fuzzing Noise Radius
                </label>
              </div>
              <span className="font-mono text-xs font-bold text-cyan-400 bg-cyan-950/60 px-2 py-0.5 rounded border border-cyan-500/30">
                ~{fuzzingRadius} meters
              </span>
            </div>
            <input
              type="range"
              min={50}
              max={500}
              step={25}
              value={fuzzingRadius}
              onChange={(e) => setFuzzingRadius(parseInt(e.target.value, 10))}
              disabled={ghostMode}
              className="w-full h-1.5 bg-slate-700 rounded-lg appearance-none cursor-pointer accent-cyan-400"
            />
            <div className="flex justify-between text-[10px] text-slate-500 font-mono">
              <span>50m (Precise Zone)</span>
              <span>100m (Standard)</span>
              <span>500m (Max Obfuscation)</span>
            </div>
          </div>

          {/* Security & Anti-Averaging Architecture Info */}
          <div className="p-3 bg-slate-950/60 border border-slate-800/60 rounded-xl space-y-1.5 text-[11px] text-slate-400">
            <div className="flex items-center gap-1.5 text-slate-300 font-semibold">
              <Lock className="w-3.5 h-3.5 text-cyan-400" />
              <span>Anti-Averaging Defense Guarantee</span>
            </div>
            <p className="leading-relaxed">
              Perturbation vectors are keyed across 5-minute temporal epochs and spatial cells. Repeated queries yield zero sample variance, defeating Central Limit Theorem averaging attacks.
            </p>
            <div className="flex items-center gap-1.5 pt-1 text-slate-400">
              <Clock className="w-3 h-3 text-purple-400" />
              <span>Ephemeral Retention: GPS trails older than 30 minutes are purged automatically.</span>
            </div>
          </div>

          {errorMessage && (
            <div className="p-3 bg-red-950/50 border border-red-500/50 rounded-xl text-xs text-red-200 flex items-center gap-2">
              <AlertCircle className="w-4 h-4 text-red-400 flex-shrink-0" />
              <span>{errorMessage}</span>
            </div>
          )}

          {saveSuccess && (
            <div className="p-3 bg-emerald-950/50 border border-emerald-500/50 rounded-xl text-xs text-emerald-200 flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-400 flex-shrink-0" />
              <span>Privacy settings synchronized securely with backend!</span>
            </div>
          )}

          {/* Action buttons */}
          <div className="flex items-center justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-xs font-semibold text-slate-400 hover:text-slate-200 hover:bg-slate-800 rounded-xl transition-all"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="flex items-center gap-2 px-5 py-2 text-xs font-bold text-slate-950 bg-gradient-to-r from-cyan-400 to-indigo-400 hover:from-cyan-300 hover:to-indigo-300 rounded-xl shadow-lg shadow-cyan-900/20 transition-all disabled:opacity-50"
            >
              {isSubmitting ? (
                <>
                  <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                  <span>Syncing...</span>
                </>
              ) : (
                <>
                  <ShieldCheck className="w-3.5 h-3.5" />
                  <span>Save Privacy Policy</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
