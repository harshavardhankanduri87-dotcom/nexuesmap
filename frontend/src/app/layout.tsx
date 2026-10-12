import type { Metadata } from "next";
import "./globals.css";
import { AuthProvider } from "@/context/AuthContext";

export const metadata: Metadata = {
  title: "NEXUSMAP — Real-Time Geospatial Communication Platform",
  description: "Next-generation campus and urban geospatial mesh network featuring real-time geofenced channels, radar HUD, and zero-leak privacy controls.",
  keywords: ["geospatial", "real-time chat", "geofencing", "OpenStreetMap", "Leaflet", "campus network"],
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className="dark">
      <body className="min-h-screen bg-[#090d16] text-slate-100 antialiased selection:bg-nexus-500 selection:text-slate-950">
        <AuthProvider>
          {children}
        </AuthProvider>
      </body>
    </html>
  );
}
