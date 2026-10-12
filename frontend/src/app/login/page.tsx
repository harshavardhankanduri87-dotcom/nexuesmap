"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { 
  Radio, 
  Lock, 
  Mail, 
  Eye, 
  EyeOff, 
  ArrowRight, 
  Loader2, 
  ShieldCheck, 
  AlertCircle,
  MapPin
} from "lucide-react";

export default function LoginPage() {
  const router = useRouter();
  const { login, isAuthenticated } = useAuth();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // If already logged in, redirect to map
  React.useEffect(() => {
    if (isAuthenticated) {
      router.push("/");
    }
  }, [isAuthenticated, router]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    if (!email || !password) {
      setErrorMessage("Please provide both email address and password.");
      return;
    }

    setIsSubmitting(true);
    const result = await login(email, password);
    setIsSubmitting(false);

    if (result.success) {
      router.push("/");
    } else {
      setErrorMessage(result.error || "Invalid email or password.");
    }
  };

  return (
    <div className="min-h-screen w-full flex items-center justify-center p-4 bg-[#090d16] relative overflow-hidden select-none">
      {/* Background Cartography Grid Glow */}
      <div className="absolute inset-0 bg-radial-grid opacity-30 pointer-events-none" />
      <div className="absolute -top-40 -left-40 w-96 h-96 rounded-full bg-cyan-500/10 blur-[120px] pointer-events-none" />
      <div className="absolute -bottom-40 -right-40 w-96 h-96 rounded-full bg-emerald-500/10 blur-[120px] pointer-events-none" />

      {/* Main Login Card */}
      <div className="relative w-full max-w-md glass-panel-glow rounded-3xl p-8 border border-slate-700/80 shadow-2xl z-10">
        
        {/* Header & Logo */}
        <div className="flex flex-col items-center text-center mb-8">
          <Link href="/" className="group mb-3">
            <div className="relative flex items-center justify-center w-14 h-14 rounded-2xl bg-gradient-to-br from-cyan-500/20 via-emerald-500/20 to-blue-500/10 border border-cyan-500/40 shadow-[0_0_25px_rgba(0,240,255,0.25)] group-hover:scale-105 transition-transform">
              <Radio className="w-7 h-7 text-cyan-400 animate-pulse" />
              <div className="absolute -top-1 -right-1 w-3 h-3 rounded-full bg-emerald-400 ring-2 ring-slate-950 animate-ping-slow" />
            </div>
          </Link>

          <h1 className="text-2xl font-black tracking-wider bg-gradient-to-r from-cyan-400 via-emerald-300 to-teal-200 bg-clip-text text-transparent">
            ACCESS NEXUS GRID
          </h1>
          <p className="text-xs text-slate-400 mt-1 font-mono">
            Authenticate to join real-time proximity mesh channels
          </p>
        </div>

        {/* Error Alert */}
        {errorMessage && (
          <div className="mb-6 p-3.5 rounded-xl bg-red-950/40 border border-red-500/40 flex items-start gap-2.5 text-xs text-red-200 animate-fadeIn">
            <AlertCircle className="w-4 h-4 text-red-400 shrink-0 mt-0.5" />
            <span className="leading-relaxed">{errorMessage}</span>
          </div>
        )}

        {/* Login Form */}
        <form onSubmit={handleSubmit} className="space-y-5">
          {/* Email Input */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300 flex items-center justify-between">
              <span>Campus / Network Email</span>
              <span className="text-[10px] text-slate-500 font-mono">ENCRYPTED</span>
            </label>
            <div className="relative flex items-center">
              <Mail className="absolute left-3.5 w-4 h-4 text-slate-400" />
              <input
                id="login-email"
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="developer@campus.edu"
                className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-slate-900/80 border border-slate-700/80 focus:border-cyan-400 focus:ring-1 focus:ring-cyan-400 text-sm text-slate-100 placeholder-slate-500 transition-all outline-none"
              />
            </div>
          </div>

          {/* Password Input */}
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <label className="text-xs font-semibold text-slate-300">
                Security Password
              </label>
            </div>
            <div className="relative flex items-center">
              <Lock className="absolute left-3.5 w-4 h-4 text-slate-400" />
              <input
                id="login-password"
                type={showPassword ? "text" : "password"}
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••••••"
                className="w-full pl-10 pr-10 py-2.5 rounded-xl bg-slate-900/80 border border-slate-700/80 focus:border-cyan-400 focus:ring-1 focus:ring-cyan-400 text-sm text-slate-100 placeholder-slate-500 transition-all outline-none"
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 text-slate-400 hover:text-slate-200 transition-colors"
                title={showPassword ? "Hide password" : "Show password"}
              >
                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
          </div>

          {/* Submit Button */}
          <button
            id="login-submit-btn"
            type="submit"
            disabled={isSubmitting}
            className="w-full py-3 rounded-xl bg-gradient-to-r from-cyan-400 via-teal-400 to-emerald-400 hover:from-cyan-300 hover:to-emerald-300 text-slate-950 font-bold text-sm tracking-wide shadow-[0_0_20px_rgba(0,240,255,0.3)] transition-all flex items-center justify-center gap-2 disabled:opacity-60 cursor-pointer disabled:cursor-not-allowed"
          >
            {isSubmitting ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                <span>Authenticating Identity...</span>
              </>
            ) : (
              <>
                <span>Sign In to Mesh</span>
                <ArrowRight className="w-4 h-4" />
              </>
            )}
          </button>
        </form>

        {/* Footer Navigation */}
        <div className="mt-8 pt-5 border-t border-slate-800 text-center space-y-3">
          <p className="text-xs text-slate-400">
            Don&apos;t have an account on NEXUSMAP?{" "}
            <Link
              href="/register"
              className="text-cyan-400 hover:text-cyan-300 font-semibold underline underline-offset-4"
            >
              Register Identity
            </Link>
          </p>

          <Link
            href="/"
            className="inline-flex items-center gap-1.5 text-[11px] font-mono text-slate-500 hover:text-slate-400 transition-colors"
          >
            <MapPin className="w-3 h-3 text-emerald-400" />
            <span>Return to Interactive Map</span>
          </Link>
        </div>

      </div>
    </div>
  );
}
