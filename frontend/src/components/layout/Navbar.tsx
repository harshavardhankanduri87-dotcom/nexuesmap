"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { 
  Radio, 
  Shield, 
  ShieldAlert, 
  Activity, 
  MapPin, 
  User as UserIcon, 
  LogOut,
  Server,
  Sparkles,
  Sliders,
} from "lucide-react";
import { BackendHealth, GeolocationStatus } from "@/types";
import { useAuth } from "@/context/AuthContext";

interface NavbarProps {
  isGhostMode: boolean;
  onToggleGhostMode: () => void;
  onOpenPrivacyModal?: () => void;
  activeZoneName?: string;
  userCoords: { lat: number; lng: number } | null;
  locationStatus?: GeolocationStatus;
}

export const Navbar: React.FC<NavbarProps> = ({
  isGhostMode,
  onToggleGhostMode,
  onOpenPrivacyModal,
  activeZoneName = "Global Campus Perimeter",
  userCoords,
  locationStatus,
}) => {
  const { user, isAuthenticated, logout } = useAuth();
  const [health, setHealth] = useState<BackendHealth | null>(null);
  const [isHealthChecking, setIsHealthChecking] = useState<boolean>(true);

  useEffect(() => {
    const fetchHealth = async () => {
      try {
        const res = await fetch("/api/v1/health", { cache: "no-store" });
        if (res.ok) {
          const data = await res.json();
          setHealth(data);
        } else {
          setHealth({
            status: "DOWN",
            service: "nexusmap-backend",
            version: "1.0.0",
            timestamp: new Date().toISOString(),
            environment: "offline",
          });
        }
      } catch (err) {
        setHealth({
          status: "DOWN",
          service: "nexusmap-backend",
          version: "1.0.0",
          timestamp: new Date().toISOString(),
          environment: "offline",
        });
      } finally {
        setIsHealthChecking(false);
      }
    };

    fetchHealth();
    const interval = setInterval(fetchHealth, 15000);
    return () => clearInterval(interval);
  }, []);

  return (
    <header className="fixed top-0 left-0 right-0 z-50 glass-panel border-b border-slate-800/80 px-4 lg:px-6 py-2.5 transition-all">
      <div className="max-w-7xl mx-auto flex items-center justify-between gap-4">
        
        {/* Brand & Platform Identity */}
        <Link href="/" className="flex items-center gap-3 group">
          <div className="relative flex items-center justify-center w-10 h-10 rounded-xl bg-gradient-to-br from-emerald-500/20 via-cyan-500/20 to-blue-500/10 border border-cyan-500/30 shadow-[0_0_15px_rgba(0,240,255,0.2)] group-hover:scale-105 transition-transform">
            <Radio className="w-5 h-5 text-cyan-400 animate-pulse" />
            <div className="absolute -top-1 -right-1 w-2.5 h-2.5 rounded-full bg-emerald-400 ring-2 ring-slate-950 animate-ping-slow" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-base font-extrabold tracking-wider bg-gradient-to-r from-cyan-400 via-emerald-300 to-teal-200 bg-clip-text text-transparent">
                NEXUSMAP
              </span>
              <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-cyan-950/80 text-cyan-400 border border-cyan-500/30 uppercase tracking-widest">
                v1.0-alpha
              </span>
            </div>
            <p className="text-[11px] text-slate-400 font-medium hidden sm:block">
              Real-Time Geospatial Mesh &amp; Geofenced Channels
            </p>
          </div>
        </Link>

        {/* Central Telemetry Badge: Current Zone & Coordinates */}
        <div className="hidden md:flex items-center gap-3 px-3.5 py-1.5 rounded-full bg-slate-900/90 border border-slate-700/60 shadow-inner">
          <div className="flex items-center gap-1.5 text-xs">
            <MapPin className="w-3.5 h-3.5 text-emerald-400" />
            <span className="text-slate-400 font-mono text-[11px]">ZONE:</span>
            <span className="text-emerald-300 font-semibold">{activeZoneName}</span>
          </div>

          <div className="h-3 w-px bg-slate-700" />

          <div className="flex items-center gap-1 text-[11px] font-mono text-slate-400">
            {userCoords ? (
              <span className="text-cyan-400">
                {userCoords.lat.toFixed(4)}, {userCoords.lng.toFixed(4)}
              </span>
            ) : (
              <span className={locationStatus === "permission_denied" ? "text-rose-400" : "text-slate-500"}>
                {locationStatus === "permission_denied"
                  ? "Permission Denied"
                  : locationStatus === "location_unavailable"
                  ? "Location Unavailable"
                  : "Acquiring GPS..."}
              </span>
            )}
          </div>
        </div>

        {/* Right Action Controls: Ghost Mode, Backend Health, Auth */}
        <div className="flex items-center gap-2.5">
          {/* Ghost Mode Privacy Toggle */}
          <button
            id="ghost-mode-toggle"
            type="button"
            onClick={onToggleGhostMode}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all border ${
              isGhostMode
                ? "bg-purple-950/80 text-purple-300 border-purple-500/60 shadow-[0_0_12px_rgba(168,85,247,0.3)]"
                : "bg-slate-900/80 text-slate-300 border-slate-700 hover:border-slate-600 hover:bg-slate-800"
            }`}
            title="Ghost Mode fuzzed coordinates prevent other users from observing exact precision"
          >
            {isGhostMode ? (
              <>
                <ShieldAlert className="w-3.5 h-3.5 text-purple-400 animate-pulse" />
                <span>Ghost Active</span>
              </>
            ) : (
              <>
                <Shield className="w-3.5 h-3.5 text-emerald-400" />
                <span className="hidden sm:inline">Ghost Mode</span>
              </>
            )}
          </button>

          {onOpenPrivacyModal && (
            <button
              type="button"
              onClick={onOpenPrivacyModal}
              className="p-1.5 rounded-lg text-slate-400 hover:text-cyan-300 hover:bg-slate-800 border border-slate-700/60 transition-all"
              title="Configure Privacy & Anti-Averaging Settings"
            >
              <Sliders className="w-3.5 h-3.5" />
            </button>
          )}

          {/* Backend Health Pill */}
          <div
            id="backend-health-indicator"
            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-mono bg-slate-900/90 border border-slate-800"
            title={
              health?.status === "UP"
                ? `Backend online: ${health.service} (${health.environment})`
                : "Backend connecting or offline (Spring Boot :8080)"
            }
          >
            <Server className="w-3 h-3 text-slate-400" />
            <span
              className={`w-2 h-2 rounded-full ${
                health?.status === "UP"
                  ? "bg-emerald-400 shadow-[0_0_8px_#34d399]"
                  : "bg-amber-400 shadow-[0_0_8px_#fbbf24] animate-pulse"
              }`}
            />
            <span className="text-[11px] text-slate-300 hidden lg:inline">
              {isHealthChecking ? "CHECKING" : health?.status === "UP" ? "GRID UP" : "STANDBY"}
            </span>
          </div>

          {/* User Authentication Menu */}
          {isAuthenticated && user ? (
            <div className="flex items-center gap-2 pl-1 border-l border-slate-800">
              <div className="flex items-center gap-2 px-2.5 py-1.5 rounded-xl bg-slate-900/90 border border-slate-700/80">
                <div className="w-6 h-6 rounded-lg bg-gradient-to-tr from-cyan-500 to-emerald-400 text-slate-950 font-black text-xs flex items-center justify-center">
                  {user.name.charAt(0).toUpperCase()}
                </div>
                <div className="hidden sm:block text-left">
                  <div className="text-xs font-bold text-slate-200 leading-none">{user.name}</div>
                  <div className="text-[10px] font-mono text-cyan-400 leading-none mt-0.5">
                    {user.role === "ROLE_ADMIN" ? "ADMIN" : "NODE"}
                  </div>
                </div>
              </div>

              {user.role === "ROLE_ADMIN" && (
                <Link
                  id="nav-admin-link"
                  href="/admin"
                  className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-xs font-bold text-amber-300 bg-amber-950/60 border border-amber-500/40 hover:bg-amber-900/60 transition-all shadow-sm"
                  title="Administrative Telemetry & Density Heatmaps"
                >
                  <ShieldAlert className="w-3.5 h-3.5 text-amber-400" />
                  <span className="hidden md:inline">Admin</span>
                </Link>
              )}

              <button
                id="nav-logout-btn"
                type="button"
                onClick={logout}
                className="p-1.5 rounded-lg text-slate-400 hover:text-red-400 hover:bg-slate-800 transition-colors"
                title="Disconnect from mesh network"
              >
                <LogOut className="w-4 h-4" />
              </button>
            </div>
          ) : (
            <div className="flex items-center gap-1.5">
              <Link
                id="nav-login-btn"
                href="/login"
                className="px-3 py-1.5 rounded-lg text-xs font-medium text-slate-300 hover:text-white hover:bg-slate-800/80 transition-colors"
              >
                Sign In
              </Link>
              <Link
                id="nav-register-btn"
                href="/register"
                className="px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-950 bg-gradient-to-r from-cyan-400 to-emerald-400 hover:from-cyan-300 hover:to-emerald-300 shadow-[0_0_12px_rgba(0,240,255,0.3)] transition-all"
              >
                Register
              </Link>
            </div>
          )}

        </div>

      </div>
    </header>
  );
};
