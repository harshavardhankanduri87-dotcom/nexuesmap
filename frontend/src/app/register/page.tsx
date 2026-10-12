"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { 
  Radio, 
  User, 
  Lock, 
  Mail, 
  Eye, 
  EyeOff, 
  ArrowRight, 
  Loader2, 
  CheckCircle2, 
  AlertCircle,
  MapPin,
  Shield
} from "lucide-react";

export default function RegisterPage() {
  const router = useRouter();
  const { register, isAuthenticated } = useAuth();

  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // If already authenticated, redirect to map
  React.useEffect(() => {
    if (isAuthenticated) {
      router.push("/");
    }
  }, [isAuthenticated, router]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    if (!name.trim()) {
      setErrorMessage("Please enter your full name.");
      return;
    }

    if (!email.trim()) {
      setErrorMessage("Please enter your email address.");
      return;
    }

    if (password.length < 8) {
      setErrorMessage("Password must be at least 8 characters in length.");
      return;
    }

    if (password !== confirmPassword) {
      setErrorMessage("Passwords do not match. Please re-enter.");
      return;
    }

    setIsSubmitting(true);
    const result = await register(name, email, password);
    setIsSubmitting(false);

    if (result.success) {
      router.push("/");
    } else {
      setErrorMessage(result.error || "Registration failed. Please try again.");
    }
  };

  const isPasswordLongEnough = password.length >= 8;
  const doPasswordsMatch = password.length > 0 && password === confirmPassword;

  return (
    <div className="min-h-screen w-full flex items-center justify-center p-4 bg-[#090d16] relative overflow-hidden select-none">
      {/* Background Cartography Grid Glow */}
      <div className="absolute inset-0 bg-radial-grid opacity-30 pointer-events-none" />
      <div className="absolute -top-40 -right-40 w-96 h-96 rounded-full bg-cyan-500/10 blur-[120px] pointer-events-none" />
      <div className="absolute -bottom-40 -left-40 w-96 h-96 rounded-full bg-emerald-500/10 blur-[120px] pointer-events-none" />

      {/* Main Register Card */}
      <div className="relative w-full max-w-md glass-panel-glow rounded-3xl p-8 border border-slate-700/80 shadow-2xl z-10">
        
        {/* Header & Brand */}
        <div className="flex flex-col items-center text-center mb-8">
          <Link href="/" className="group mb-3">
            <div className="relative flex items-center justify-center w-14 h-14 rounded-2xl bg-gradient-to-br from-emerald-500/20 via-cyan-500/20 to-blue-500/10 border border-cyan-500/40 shadow-[0_0_25px_rgba(0,240,255,0.25)] group-hover:scale-105 transition-transform">
              <Radio className="w-7 h-7 text-cyan-400 animate-pulse" />
              <div className="absolute -top-1 -right-1 w-3 h-3 rounded-full bg-emerald-400 ring-2 ring-slate-950 animate-ping-slow" />
            </div>
          </Link>

          <h1 className="text-2xl font-black tracking-wider bg-gradient-to-r from-emerald-300 via-cyan-400 to-teal-200 bg-clip-text text-transparent">
            CREATE IDENTITY
          </h1>
          <p className="text-xs text-slate-400 mt-1 font-mono">
            Register your node for geospatial campus mesh channels
          </p>
        </div>

        {/* Error Alert */}
        {errorMessage && (
          <div className="mb-6 p-3.5 rounded-xl bg-red-950/40 border border-red-500/40 flex items-start gap-2.5 text-xs text-red-200 animate-fadeIn">
            <AlertCircle className="w-4 h-4 text-red-400 shrink-0 mt-0.5" />
            <span className="leading-relaxed">{errorMessage}</span>
          </div>
        )}

        {/* Form */}
        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Full Name */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300">
              Display Name
            </label>
            <div className="relative flex items-center">
              <User className="absolute left-3.5 w-4 h-4 text-slate-400" />
              <input
                id="register-name"
                type="text"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Alex Turing"
                className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-slate-900/80 border border-slate-700/80 focus:border-cyan-400 focus:ring-1 focus:ring-cyan-400 text-sm text-slate-100 placeholder-slate-500 transition-all outline-none"
              />
            </div>
          </div>

          {/* Email Address */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300">
              Campus / Institutional Email
            </label>
            <div className="relative flex items-center">
              <Mail className="absolute left-3.5 w-4 h-4 text-slate-400" />
              <input
                id="register-email"
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="alex@campus.edu"
                className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-slate-900/80 border border-slate-700/80 focus:border-cyan-400 focus:ring-1 focus:ring-cyan-400 text-sm text-slate-100 placeholder-slate-500 transition-all outline-none"
              />
            </div>
          </div>

          {/* Password */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300">
              Password (Min 8 Characters)
            </label>
            <div className="relative flex items-center">
              <Lock className="absolute left-3.5 w-4 h-4 text-slate-400" />
              <input
                id="register-password"
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
              >
                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
          </div>

          {/* Confirm Password */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300">
              Confirm Password
            </label>
            <div className="relative flex items-center">
              <Shield className="absolute left-3.5 w-4 h-4 text-slate-400" />
              <input
                id="register-confirm-password"
                type={showPassword ? "text" : "password"}
                required
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="••••••••••••"
                className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-slate-900/80 border border-slate-700/80 focus:border-cyan-400 focus:ring-1 focus:ring-cyan-400 text-sm text-slate-100 placeholder-slate-500 transition-all outline-none"
              />
            </div>
          </div>

          {/* Password requirements indicators */}
          <div className="flex items-center gap-4 text-[11px] pt-1 text-slate-400">
            <span className={`flex items-center gap-1 ${isPasswordLongEnough ? "text-emerald-400" : "text-slate-500"}`}>
              <CheckCircle2 className="w-3.5 h-3.5" /> 8+ Characters
            </span>
            <span className={`flex items-center gap-1 ${doPasswordsMatch ? "text-emerald-400" : "text-slate-500"}`}>
              <CheckCircle2 className="w-3.5 h-3.5" /> Passwords Match
            </span>
          </div>

          {/* Submit */}
          <button
            id="register-submit-btn"
            type="submit"
            disabled={isSubmitting}
            className="w-full py-3 mt-2 rounded-xl bg-gradient-to-r from-emerald-400 via-teal-400 to-cyan-400 hover:from-emerald-300 hover:to-cyan-300 text-slate-950 font-bold text-sm tracking-wide shadow-[0_0_20px_rgba(0,230,157,0.3)] transition-all flex items-center justify-center gap-2 disabled:opacity-60 cursor-pointer disabled:cursor-not-allowed"
          >
            {isSubmitting ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                <span>Hashing &amp; Provisioning Node...</span>
              </>
            ) : (
              <>
                <span>Complete Registration</span>
                <ArrowRight className="w-4 h-4" />
              </>
            )}
          </button>
        </form>

        {/* Footer Navigation */}
        <div className="mt-8 pt-5 border-t border-slate-800 text-center space-y-3">
          <p className="text-xs text-slate-400">
            Already registered on the platform?{" "}
            <Link
              href="/login"
              className="text-cyan-400 hover:text-cyan-300 font-semibold underline underline-offset-4"
            >
              Sign In
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
