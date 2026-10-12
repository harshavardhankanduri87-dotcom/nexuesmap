package com.nexusmap.util;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import java.util.ArrayList;
import java.util.List;

public final class GeometryUtils {

    public static final int SRID_WGS84 = 4326;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), SRID_WGS84);
    private static final double EARTH_RADIUS_METERS = 6371008.8;

    private GeometryUtils() {}

    /**
     * Creates a JTS Point from latitude and longitude.
     * In GIS, X = Longitude, Y = Latitude.
     */
    public static Point createPoint(double latitude, double longitude) {
        validateCoordinates(latitude, longitude);
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(SRID_WGS84);
        return point;
    }

    /**
     * Creates a JTS Polygon from a list of [longitude, latitude] coordinates.
     * Ensures the ring is closed.
     */
    public static Polygon createPolygon(List<List<Double>> coordinatePairs) {
        if (coordinatePairs == null || coordinatePairs.size() < 3) {
            throw new IllegalArgumentException("A polygon requires at least 3 distinct coordinates.");
        }

        List<Coordinate> coordinates = new ArrayList<>();
        for (List<Double> pair : coordinatePairs) {
            if (pair.size() < 2) {
                throw new IllegalArgumentException("Coordinate pair must contain [lng, lat]");
            }
            double lng = pair.get(0);
            double lat = pair.get(1);
            validateCoordinates(lat, lng);
            coordinates.add(new Coordinate(lng, lat));
        }

        // Close the ring if not already closed
        Coordinate first = coordinates.get(0);
        Coordinate last = coordinates.get(coordinates.size() - 1);
        if (first.x != last.x || first.y != last.y) {
            coordinates.add(new Coordinate(first.x, first.y));
        }

        if (coordinates.size() < 4) {
            throw new IllegalArgumentException("A closed linear ring requires at least 4 coordinates.");
        }

        LinearRing shell = GEOMETRY_FACTORY.createLinearRing(coordinates.toArray(new Coordinate[0]));
        Polygon polygon = GEOMETRY_FACTORY.createPolygon(shell);
        polygon.setSRID(SRID_WGS84);
        return polygon;
    }

    /**
     * Creates a JTS Polygon from an array of Coordinates.
     */
    public static Polygon createPolygon(Coordinate[] coordinates) {
        LinearRing shell = GEOMETRY_FACTORY.createLinearRing(coordinates);
        Polygon polygon = GEOMETRY_FACTORY.createPolygon(shell);
        polygon.setSRID(SRID_WGS84);
        return polygon;
    }

    /**
     * Parses WKT string into JTS Geometry with SRID 4326.
     */
    public static Geometry fromWkt(String wkt) {
        try {
            WKTReader reader = new WKTReader(GEOMETRY_FACTORY);
            Geometry geom = reader.read(wkt);
            geom.setSRID(SRID_WGS84);
            return geom;
        } catch (ParseException e) {
            throw new IllegalArgumentException("Failed to parse WKT geometry: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts coordinates as GeoJSON format: [[lng, lat], ...]
     */
    public static List<List<Double>> toGeoJsonCoordinates(Polygon polygon) {
        List<List<Double>> result = new ArrayList<>();
        if (polygon == null) return result;

        Coordinate[] coords = polygon.getExteriorRing().getCoordinates();
        for (Coordinate coord : coords) {
            result.add(List.of(coord.x, coord.y)); // [lng, lat]
        }
        return result;
    }

    /**
     * Extracts coordinates as Leaflet format: [[lat, lng], ...]
     */
    public static List<List<Double>> toLeafletCoordinates(Polygon polygon) {
        List<List<Double>> result = new ArrayList<>();
        if (polygon == null) return result;

        Coordinate[] coords = polygon.getExteriorRing().getCoordinates();
        for (Coordinate coord : coords) {
            result.add(List.of(coord.y, coord.x)); // [lat, lng]
        }
        return result;
    }

    /**
     * Calculates distance in meters between two WGS84 points using Haversine formula.
     */
    public static double calculateDistanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Validates that latitude and longitude fall within legal WGS84 ranges.
     */
    public static void validateCoordinates(double latitude, double longitude) {
        if (Double.isNaN(latitude) || Double.isInfinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Invalid latitude: " + latitude + ". Must be between -90.0 and 90.0.");
        }
        if (Double.isNaN(longitude) || Double.isInfinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Invalid longitude: " + longitude + ". Must be between -180.0 and 180.0.");
        }
    }
}
