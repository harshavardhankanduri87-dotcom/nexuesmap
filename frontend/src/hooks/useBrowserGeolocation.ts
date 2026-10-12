"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import { Coordinates, GeolocationStatus } from "@/types";

interface UseBrowserGeolocationReturn {
  coords: Coordinates | null;
  status: GeolocationStatus;
  error: string | null;
  recenterTrigger: number;
  requestLocation: () => void;
  recenter: () => void;
}

export function useBrowserGeolocation(): UseBrowserGeolocationReturn {
  const [coords, setCoords] = useState<Coordinates | null>(null);
  const [status, setStatus] = useState<GeolocationStatus>("getting_location");
  const [error, setError] = useState<string | null>(null);
  const [recenterTrigger, setRecenterTrigger] = useState<number>(0);

  const watchIdRef = useRef<number | null>(null);

  const handlePositionSuccess = useCallback((position: GeolocationPosition) => {
    const { latitude, longitude, accuracy } = position.coords;
    setCoords({
      lat: latitude,
      lng: longitude,
      accuracy: accuracy || undefined,
    });
    setStatus("location_available");
    setError(null);
  }, []);

  const handlePositionError = useCallback((geoError: GeolocationPositionError) => {
    switch (geoError.code) {
      case geoError.PERMISSION_DENIED:
        setStatus("permission_denied");
        setError(
          "Location permission denied. Please grant location permissions in your browser to activate live positioning and geofencing."
        );
        break;
      case geoError.POSITION_UNAVAILABLE:
        setStatus("location_unavailable");
        setError(
          "Location unavailable. GPS satellite or network positioning fix could not be acquired."
        );
        break;
      case geoError.TIMEOUT:
        setStatus("location_unavailable");
        setError(
          "Location request timed out while acquiring high-accuracy GPS fix."
        );
        break;
      default:
        setStatus("location_unavailable");
        setError("An unknown error occurred while requesting device coordinates.");
        break;
    }
  }, []);

  const requestLocation = useCallback(() => {
    if (typeof window === "undefined" || !navigator.geolocation) {
      setStatus("location_unavailable");
      setError("Geolocation is not supported by your browser.");
      return;
    }

    setStatus("getting_location");
    setError(null);

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        handlePositionSuccess(pos);
        // Trigger map recenter on explicit request
        setRecenterTrigger((prev) => prev + 1);
      },
      (err) => {
        handlePositionError(err);
      },
      {
        enableHighAccuracy: true,
        timeout: 10000,
        maximumAge: 0,
      }
    );
  }, [handlePositionSuccess, handlePositionError]);

  const recenter = useCallback(() => {
    if (coords) {
      setRecenterTrigger((prev) => prev + 1);
    } else {
      requestLocation();
    }
  }, [coords, requestLocation]);

  // Initial acquisition & watch tracking
  useEffect(() => {
    if (typeof window === "undefined" || !navigator.geolocation) {
      setStatus("location_unavailable");
      setError("Geolocation is not supported by your browser.");
      return;
    }

    // 1. Initial immediate position query
    navigator.geolocation.getCurrentPosition(
      handlePositionSuccess,
      handlePositionError,
      {
        enableHighAccuracy: true,
        timeout: 12000,
        maximumAge: 5000,
      }
    );

    // 2. Continuous watchPosition to track movement
    const watchId = navigator.geolocation.watchPosition(
      handlePositionSuccess,
      handlePositionError,
      {
        enableHighAccuracy: true,
        timeout: 15000,
        maximumAge: 5000,
      }
    );
    watchIdRef.current = watchId;

    return () => {
      if (watchIdRef.current !== null && navigator.geolocation) {
        navigator.geolocation.clearWatch(watchIdRef.current);
      }
    };
  }, [handlePositionSuccess, handlePositionError]);

  return {
    coords,
    status,
    error,
    recenterTrigger,
    requestLocation,
    recenter,
  };
}
