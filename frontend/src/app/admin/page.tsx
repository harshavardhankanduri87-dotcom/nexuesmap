"use client";

import React, { useState, useEffect } from "react";
import dynamic from "next/dynamic";
import Link from "next/link";
import { useAuth } from "@/context/AuthContext";
import {
  AdminOverview,
  ZoneOccupancy,
  ActivityTrend,
  DensityHeatmap,
} from "@/types";
import {
  Users,
  Radio,
  MapPin,
  Calendar,
  MessageSquare,
  Activity,
  Flame,
  Shield,
  ShieldAlert,
  ArrowLeft,
  RefreshCw,
  Sliders,
  CheckCircle2,
  AlertTriangle,
  Clock,
  Layers,
  ChevronRight,
  Info,
} from "lucide-react";
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  Legend,
  AreaChart,
  Area,
  CartesianGrid,
} from "recharts";

// Dynamically import Leaflet Heatmap with SSR disabled
const DynamicAdminMapHeatmap = dynamic(
  () =>
    import("@/components/admin/AdminMapHeatmap").then(
      (mod) => mod.AdminMapHeatmap
    ),
  {
    ssr: false,
    loading: () => (
      <div className="w-full h-[450px] rounded-2xl bg-slate-900 border border-slate-800 flex items-center justify-center text-slate-400">
        <RefreshCw className="w-6 h-6 animate-spin text-cyan-400 mr-2" />
        <span>Loading Cartographic Density Engine...</span>
      </div>
    ),
  }
);

export default function AdminDashboardPage() {
  const { user, isAuthenticated, isLoading: authLoading } = useAuth();

  const [overview, setOverview] = useState<AdminOverview | null>(null);
  const [zones, setZones] = useState<ZoneOccupancy[]>([]);
  const [activity, setActivity] = useState<ActivityTrend | null>(null);
  const [density, setDensity] = useState<DensityHeatmap | null>(null);

  const [selectedInterval, setSelectedInterval] = useState<string>("24h");
  const [minThreshold, setMinThreshold] = useState<number>(3);

  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [showDefinitions, setShowDefinitions] = useState<boolean>(false);

  // Fetch all analytics datasets
  const fetchDashboardData = async () => {
    setIsLoading(true);
    setErrorMessage(null);

    try {
      // 1. Overview KPIs
      const overviewRes = await fetch("/api/admin/analytics/overview", {
        credentials: "include",
      });
      if (!overviewRes.ok) {
        throw new Error(`Overview fetch failed: HTTP ${overviewRes.status}`);
      }
      const overviewData: AdminOverview = await overviewRes.json();
      setOverview(overviewData);

      // 2. Zone Occupancy
      const zonesRes = await fetch("/api/admin/analytics/zones", {
        credentials: "include",
      });
      if (zonesRes.ok) {
        const zonesData: ZoneOccupancy[] = await zonesRes.json();
        setZones(zonesData);
      }

      // 3. Activity Trends
      const actRes = await fetch(
        `/api/admin/analytics/activity?interval=${selectedInterval}`,
        { credentials: "include" }
      );
      if (actRes.ok) {
        const actData: ActivityTrend = await actRes.json();
        setActivity(actData);
      }

      // 4. Density Heatmap
      const densRes = await fetch(
        `/api/admin/analytics/density?minThreshold=${minThreshold}`,
        { credentials: "include" }
      );
      if (densRes.ok) {
        const densData: DensityHeatmap = await densRes.json();
        setDensity(densData);
      }
    } catch (err: any) {
      console.error("Admin dashboard fetch error:", err);
      setErrorMessage(
        err.message || "Failed to load administrative analytics."
      );
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (isAuthenticated && user?.role === "ROLE_ADMIN") {
      fetchDashboardData();
    }
  }, [isAuthenticated, user, selectedInterval, minThreshold]);

  // Authorization Check
  if (authLoading) {
    return (
      <div className="min-h-screen bg-[#090d16] flex items-center justify-center text-cyan-400">
        <RefreshCw className="w-8 h-8 animate-spin" />
      </div>
    );
  }

  if (!isAuthenticated || user?.role !== "ROLE_ADMIN") {
    return (
      <div className="min-h-screen bg-[#090d16] flex flex-col items-center justify-center p-6 text-slate-100">
        <div className="max-w-md w-full p-8 rounded-2xl bg-slate-900 border border-red-500/30 text-center space-y-4 shadow-2xl">
          <div className="inline-flex p-3 rounded-2xl bg-red-950/60 border border-red-500/50 text-red-400">
            <ShieldAlert className="w-8 h-8" />
          </div>
          <h1 className="text-xl font-bold text-slate-100">
            403 Forbidden — Admin Access Required
          </h1>
          <p className="text-xs text-slate-400 leading-relaxed">
            This dashboard is restricted to authorized campus administrators
            with <code className="text-red-300 font-mono">ROLE_ADMIN</code> privileges.
          </p>
          <div className="pt-2 flex justify-center gap-3">
            <Link
              href="/"
              className="px-4 py-2 text-xs font-bold text-slate-900 bg-cyan-400 hover:bg-cyan-300 rounded-xl transition-all"
            >
              Return to Map
            </Link>
            <Link
              href="/login"
              className="px-4 py-2 text-xs font-semibold text-slate-300 bg-slate-800 hover:bg-slate-700 rounded-xl border border-slate-700 transition-all"
            >
              Switch Account
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <main className="min-h-screen bg-[#090d16] text-slate-100 pb-16">
      {/* Top Navbar */}
      <header className="sticky top-0 z-30 h-16 bg-slate-900/80 backdrop-blur-md border-b border-slate-800 px-6 flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Link
            href="/"
            className="flex items-center gap-1.5 text-xs text-slate-400 hover:text-cyan-300 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Map View</span>
          </Link>
          <div className="h-4 w-px bg-slate-800" />
          <div className="flex items-center gap-2">
            <Shield className="w-5 h-5 text-cyan-400" />
            <h1 className="text-sm font-bold tracking-wider text-slate-100 uppercase">
              NEXUSMAP Command & Analytics
            </h1>
            <span className="px-2 py-0.5 rounded text-[10px] font-mono font-bold bg-cyan-950 text-cyan-300 border border-cyan-500/30">
              ROLE_ADMIN
            </span>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={() => setShowDefinitions((prev) => !prev)}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700 transition-all"
          >
            <Info className="w-3.5 h-3.5 text-cyan-400" />
            <span>Metric Specs</span>
          </button>
          <button
            onClick={fetchDashboardData}
            disabled={isLoading}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold text-slate-950 bg-cyan-400 hover:bg-cyan-300 transition-all disabled:opacity-50"
          >
            <RefreshCw
              className={`w-3.5 h-3.5 ${isLoading ? "animate-spin" : ""}`}
            />
            <span>Refresh</span>
          </button>
        </div>
      </header>

      <div className="max-w-7xl mx-auto px-6 pt-6 space-y-6">
        {/* Error Alert */}
        {errorMessage && (
          <div className="p-4 rounded-xl bg-red-950/60 border border-red-500/50 flex items-center justify-between text-xs text-red-200">
            <div className="flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 text-red-400 shrink-0" />
              <span>{errorMessage}</span>
            </div>
            <button
              onClick={fetchDashboardData}
              className="font-bold underline text-red-300 hover:text-white"
            >
              Retry
            </button>
          </div>
        )}

        {/* Metric Specs Drawer / Collapsible Box */}
        {showDefinitions && overview?.metricDefinitions && (
          <div className="p-4 rounded-2xl bg-slate-900/90 border border-slate-800 space-y-2 animate-fade-in text-xs">
            <div className="flex items-center justify-between border-b border-slate-800 pb-2">
              <h3 className="font-bold text-slate-200 flex items-center gap-1.5">
                <Info className="w-4 h-4 text-cyan-400" />
                <span>Audited Metric Calculation Specifications</span>
              </h3>
              <span className="text-[10px] text-slate-500 font-mono">
                TASK 5 Compliance
              </span>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3 pt-2 text-[11px] text-slate-400">
              {Object.entries(overview.metricDefinitions).map(([key, def]) => (
                <div key={key} className="p-2.5 rounded-lg bg-slate-950/60 border border-slate-850">
                  <span className="font-mono text-cyan-400 font-bold block mb-0.5">
                    {key}
                  </span>
                  <span className="leading-relaxed">{def}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 1. Overview KPI Cards Grid */}
        <section className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3.5">
          {/* Card 1: Registered Accounts */}
          <div className="p-4 rounded-2xl bg-slate-900/70 border border-slate-800 shadow-lg space-y-1">
            <div className="flex items-center justify-between text-slate-400">
              <span className="text-xs font-semibold">Registered Users</span>
              <Users className="w-4 h-4 text-cyan-400" />
            </div>
            <div className="text-2xl font-bold font-mono text-slate-100">
              {overview?.totalRegisteredUsers ?? "—"}
            </div>
            <p className="text-[10px] text-slate-500">Total accounts</p>
          </div>

          {/* Card 2: Active Telemetry Nodes */}
          <div className="p-4 rounded-2xl bg-slate-900/70 border border-slate-800 shadow-lg space-y-1">
            <div className="flex items-center justify-between text-slate-400">
              <span className="text-xs font-semibold">Active Users</span>
              <Activity className="w-4 h-4 text-emerald-400 animate-pulse" />
            </div>
            <div className="text-2xl font-bold font-mono text-emerald-400">
              {overview?.currentlyActiveUsers ?? "—"}
            </div>
            <p className="text-[10px] text-slate-500">Past 15 minutes</p>
          </div>

          {/* Card 3: Geofenced Zones */}
          <div className="p-4 rounded-2xl bg-slate-900/70 border border-slate-800 shadow-lg space-y-1">
            <div className="flex items-center justify-between text-slate-400">
              <span className="text-xs font-semibold">Active Zones</span>
              <Radio className="w-4 h-4 text-indigo-400" />
            </div>
            <div className="text-2xl font-bold font-mono text-slate-100">
              {overview?.activeZonesCount ?? "—"}
            </div>
            <p className="text-[10px] text-slate-500">PostGIS polygons</p>
          </div>

          {/* Card 4: Global Pins */}
          <div className="p-4 rounded-2xl bg-slate-900/70 border border-slate-800 shadow-lg space-y-1">
            <div className="flex items-center justify-between text-slate-400">
              <span className="text-xs font-semibold">Active Pins</span>
              <MapPin className="w-4 h-4 text-amber-400" />
            </div>
            <div className="text-2xl font-bold font-mono text-slate-100">
              {overview?.activePinsCount ?? "—"}
            </div>
            <p className="text-[10px] text-slate-500">Unexpired markers</p>
          </div>

          {/* Card 5: Collaborative Meetups */}
          <div className="p-4 rounded-2xl bg-slate-900/70 border border-slate-800 shadow-lg space-y-1">
            <div className="flex items-center justify-between text-slate-400">
              <span className="text-xs font-semibold">Active Meetups</span>
              <Calendar className="w-4 h-4 text-purple-400" />
            </div>
            <div className="text-2xl font-bold font-mono text-slate-100">
              {overview?.activeMeetupsCount ?? "—"}
            </div>
            <p className="text-[10px] text-slate-500">Scheduled events</p>
          </div>

          {/* Card 6: Message Traffic */}
          <div className="p-4 rounded-2xl bg-slate-900/70 border border-slate-800 shadow-lg space-y-1">
            <div className="flex items-center justify-between text-slate-400">
              <span className="text-xs font-semibold">Messages (24h)</span>
              <MessageSquare className="w-4 h-4 text-sky-400" />
            </div>
            <div className="text-2xl font-bold font-mono text-slate-100">
              {overview?.messagesInLast24Hours ?? "—"}
            </div>
            <p className="text-[10px] text-slate-500">
              Total: {overview?.totalMessagesCount ?? "—"}
            </p>
          </div>
        </section>

        {/* 2. Geographic Density Heatmap Section */}
        <section className="space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Flame className="w-5 h-5 text-orange-400" />
              <h2 className="text-base font-bold text-slate-100">
                Geographic User Density Heatmap
              </h2>
            </div>
            <span className="text-xs text-slate-400 font-mono">
              k-Anonymity Threshold: ≥{minThreshold} Users
            </span>
          </div>

          <DynamicAdminMapHeatmap
            densityData={density}
            minThreshold={minThreshold}
            onChangeThreshold={setMinThreshold}
            isLoading={isLoading}
          />
        </section>

        {/* 3. Analytics Charts Grid */}
        <section className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Chart 1: Zone Occupancy Breakdown */}
          <div className="p-5 rounded-2xl bg-slate-900/70 border border-slate-800 shadow-xl space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-sm font-bold text-slate-100 flex items-center gap-2">
                  <Radio className="w-4 h-4 text-indigo-400" />
                  <span>Campus Zone Occupancy</span>
                </h3>
                <p className="text-[11px] text-slate-400">
                  Total vs. Privacy-Eligible Active Members
                </p>
              </div>
              <span className="text-[10px] font-mono text-cyan-400 bg-cyan-950/60 px-2 py-1 rounded border border-cyan-500/30">
                {zones.length} Zones
              </span>
            </div>

            <div className="h-64 w-full">
              {zones.length > 0 ? (
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart
                    data={zones}
                    margin={{ top: 10, right: 10, left: -20, bottom: 20 }}
                  >
                    <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                    <XAxis
                      dataKey="zoneName"
                      stroke="#64748b"
                      fontSize={10}
                      tickLine={false}
                      angle={-25}
                      textAnchor="end"
                    />
                    <YAxis stroke="#64748b" fontSize={10} />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: "#0f172a",
                        borderColor: "#334155",
                        borderRadius: "0.75rem",
                        fontSize: "11px",
                      }}
                    />
                    <Legend wrapperStyle={{ fontSize: "11px" }} />
                    <Bar
                      dataKey="activeMembers"
                      name="Total Active"
                      fill="#38bdf8"
                      radius={[4, 4, 0, 0]}
                    />
                    <Bar
                      dataKey="privacyEligibleMembers"
                      name="Privacy Eligible (Non-Ghost)"
                      fill="#a855f7"
                      radius={[4, 4, 0, 0]}
                    />
                  </BarChart>
                </ResponsiveContainer>
              ) : (
                <div className="h-full flex items-center justify-center text-xs text-slate-500 font-mono">
                  No active zone telemetry recorded.
                </div>
              )}
            </div>
          </div>

          {/* Chart 2: Activity Over Time */}
          <div className="p-5 rounded-2xl bg-slate-900/70 border border-slate-800 shadow-xl space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-sm font-bold text-slate-100 flex items-center gap-2">
                  <Activity className="w-4 h-4 text-emerald-400" />
                  <span>Activity Trends Over Time</span>
                </h3>
                <p className="text-[11px] text-slate-400">
                  Messages, GPS updates & pin creations
                </p>
              </div>

              {/* Interval Selector */}
              <div className="flex items-center gap-1 bg-slate-950 p-1 rounded-xl border border-slate-800">
                {["24h", "7d", "30d"].map((intvl) => (
                  <button
                    key={intvl}
                    type="button"
                    onClick={() => setSelectedInterval(intvl)}
                    className={`px-2.5 py-1 rounded-lg text-[10px] font-bold transition-all ${
                      selectedInterval === intvl
                        ? "bg-cyan-500 text-slate-950 shadow-sm"
                        : "text-slate-400 hover:text-white"
                    }`}
                  >
                    {intvl}
                  </button>
                ))}
              </div>
            </div>

            <div className="h-64 w-full">
              {activity && activity.buckets.length > 0 ? (
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart
                    data={activity.buckets}
                    margin={{ top: 10, right: 10, left: -20, bottom: 0 }}
                  >
                    <defs>
                      <linearGradient id="msgGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#00f0ff" stopOpacity={0.3} />
                        <stop offset="95%" stopColor="#00f0ff" stopOpacity={0} />
                      </linearGradient>
                      <linearGradient id="pinGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#f59e0b" stopOpacity={0.3} />
                        <stop offset="95%" stopColor="#f59e0b" stopOpacity={0} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                    <XAxis
                      dataKey="label"
                      stroke="#64748b"
                      fontSize={10}
                      tickLine={false}
                    />
                    <YAxis stroke="#64748b" fontSize={10} />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: "#0f172a",
                        borderColor: "#334155",
                        borderRadius: "0.75rem",
                        fontSize: "11px",
                      }}
                    />
                    <Legend wrapperStyle={{ fontSize: "11px" }} />
                    <Area
                      type="monotone"
                      dataKey="messageCount"
                      name="Messages"
                      stroke="#00f0ff"
                      fillOpacity={1}
                      fill="url(#msgGrad)"
                    />
                    <Area
                      type="monotone"
                      dataKey="pinCreationCount"
                      name="Pins Created"
                      stroke="#f59e0b"
                      fillOpacity={1}
                      fill="url(#pinGrad)"
                    />
                  </AreaChart>
                </ResponsiveContainer>
              ) : (
                <div className="h-full flex items-center justify-center text-xs text-slate-500 font-mono">
                  No activity trend records in selected interval.
                </div>
              )}
            </div>
          </div>
        </section>
      </div>
    </main>
  );
}
