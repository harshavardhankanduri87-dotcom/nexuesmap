"use client";

import React, { createContext, useContext, useEffect, useState, useCallback } from "react";
import { UserSummary, AuthResponse } from "@/types";

interface AuthContextType {
  user: UserSummary | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
  login: (email: string, password: string) => Promise<{ success: boolean; error?: string }>;
  register: (name: string, email: string, password: string) => Promise<{ success: boolean; error?: string }>;
  logout: () => Promise<void>;
  refreshProfile: () => Promise<void>;
  clearError: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserSummary | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Check current user session using HttpOnly cookie
  const refreshProfile = useCallback(async () => {
    try {
      const res = await fetch("/api/auth/me", {
        method: "GET",
        headers: {
          "Accept": "application/json",
        },
        credentials: "include", // Ensures HttpOnly cookie is transmitted
      });

      if (res.ok) {
        const userData: UserSummary = await res.json();
        setUser(userData);
        setError(null);
      } else {
        setUser(null);
      }
    } catch (err) {
      setUser(null);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshProfile();
  }, [refreshProfile]);

  const login = async (email: string, password: string): Promise<{ success: boolean; error?: string }> => {
    setIsLoading(true);
    setError(null);
    try {
      const res = await fetch("/api/auth/login", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Accept": "application/json",
        },
        credentials: "include",
        body: JSON.stringify({ email, password }),
      });

      const data = await res.json();

      if (!res.ok) {
        const errorMessage = data.message || data.error || "Authentication failed. Please verify credentials.";
        setError(errorMessage);
        setIsLoading(false);
        return { success: false, error: errorMessage };
      }

      const authResponse: AuthResponse = data;
      if (typeof window !== "undefined" && authResponse.token) {
        localStorage.setItem("nexus_jwt", authResponse.token);
      }
      setUser(authResponse.user);
      setError(null);
      setIsLoading(false);
      return { success: true };
    } catch (err: any) {
      const msg = err.message || "Network error while connecting to authentication service.";
      setError(msg);
      setIsLoading(false);
      return { success: false, error: msg };
    }
  };

  const register = async (
    name: string,
    email: string,
    password: string
  ): Promise<{ success: boolean; error?: string }> => {
    setIsLoading(true);
    setError(null);
    try {
      const res = await fetch("/api/auth/register", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Accept": "application/json",
        },
        credentials: "include",
        body: JSON.stringify({ name, email, password }),
      });

      const data = await res.json();

      if (!res.ok) {
        let errorMessage = data.message || "Registration failed.";
        if (data.validationErrors) {
          const firstErr = Object.values(data.validationErrors)[0] as string;
          errorMessage = firstErr || errorMessage;
        }
        setError(errorMessage);
        setIsLoading(false);
        return { success: false, error: errorMessage };
      }

      const authResponse: AuthResponse = data;
      if (typeof window !== "undefined" && authResponse.token) {
        localStorage.setItem("nexus_jwt", authResponse.token);
      }
      setUser(authResponse.user);
      setError(null);
      setIsLoading(false);
      return { success: true };
    } catch (err: any) {
      const msg = err.message || "Network error while connecting to authentication service.";
      setError(msg);
      setIsLoading(false);
      return { success: false, error: msg };
    }
  };

  const logout = async (): Promise<void> => {
    try {
      await fetch("/api/auth/logout", {
        method: "POST",
        credentials: "include",
      });
    } catch (e) {
      // Continue client cleanup even if network fails
    } finally {
      if (typeof window !== "undefined") {
        localStorage.removeItem("nexus_jwt");
      }
      setUser(null);
      setError(null);
    }
  };

  const clearError = () => setError(null);

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isLoading,
        error,
        login,
        register,
        logout,
        refreshProfile,
        clearError,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
};
