package be.kuleuven.gt.grvlfinder;

import android.content.Context;
import android.util.Log;
import org.osmdroid.util.GeoPoint;
import java.util.List;

/**
 * Helper class to ensure GPX files from drawn routes have proper elevation data
 */
public class GpxElevationHelper {
    private static final String TAG = "GpxElevationHelper";

    public interface ElevationCallback {
        void onElevationAdded(List<GeoPoint> routeWithElevation);
        void onError(String error);
    }

    /**
     * Add elevation data to a drawn route before exporting to GPX
     */
    public static void addElevationToRoute(Context context, List<GeoPoint> route, ElevationCallback callback) {
        if (route == null || route.isEmpty()) {
            callback.onError("No route provided");
            return;
        }

        // Check if route already has elevation data
        boolean hasElevation = false;
        for (GeoPoint point : route) {
            if (point.getAltitude() != 0.0) {
                hasElevation = true;
                break;
            }
        }

        if (hasElevation) {
            Log.d(TAG, "Route already has elevation data");
            callback.onElevationAdded(route);
            return;
        }

        Log.d(TAG, "Fetching elevation data for " + route.size() + " points");

        // Sample route points if too many (max 100 points for API limits)
        List<GeoPoint> sampledPoints = sampleRoutePoints(route, 50.0, 100);

        Log.d(TAG, "Sampling to " + sampledPoints.size() + " points for elevation lookup");

        // Create dummy road for elevation service
        PolylineResult dummyRoad = new PolylineResult(sampledPoints, 0, new java.util.HashMap<>());
        List<PolylineResult> roads = new java.util.ArrayList<>();
        roads.add(dummyRoad);

        // Fetch elevation data
        ElevationService.addSlopeDataToRoads(roads, new ElevationService.RoadElevationCallback() {
            @Override
            public void onSuccess(List<PolylineResult> updatedResults) {
                Log.d(TAG, "Elevation data fetched successfully");

                // Get the updated points with elevation
                List<GeoPoint> sampledWithElevation = updatedResults.get(0).getPoints();

                // Interpolate elevation back to all original route points
                List<GeoPoint> routeWithElevation = interpolateElevation(route, sampledWithElevation);

                callback.onElevationAdded(routeWithElevation);
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "Failed to fetch elevation: " + error);
                callback.onError(error);
            }
        });
    }

    /**
     * Sample route points for elevation lookup (avoid hitting API limits)
     */
    private static List<GeoPoint> sampleRoutePoints(List<GeoPoint> route, double intervalMeters, int maxPoints) {
        List<GeoPoint> sampled = new java.util.ArrayList<>();
        sampled.add(route.get(0)); // Always include first point

        double accumulatedDistance = 0;
        double nextSampleDistance = intervalMeters;

        for (int i = 1; i < route.size() && sampled.size() < maxPoints; i++) {
            GeoPoint prev = route.get(i - 1);
            GeoPoint curr = route.get(i);
            double segmentDist = prev.distanceToAsDouble(curr);

            accumulatedDistance += segmentDist;

            if (accumulatedDistance >= nextSampleDistance) {
                sampled.add(curr);
                nextSampleDistance += intervalMeters;
            }
        }

        // Always include last point
        if (!sampled.contains(route.get(route.size() - 1))) {
            sampled.add(route.get(route.size() - 1));
        }

        return sampled;
    }

    /**
     * Interpolate elevation from sampled points to all route points
     */
    private static List<GeoPoint> interpolateElevation(List<GeoPoint> originalRoute, List<GeoPoint> sampledWithElevation) {
        List<GeoPoint> result = new java.util.ArrayList<>();

        for (GeoPoint routePoint : originalRoute) {
            // Find nearest sampled point with elevation
            double minDist = Double.MAX_VALUE;
            GeoPoint nearestSampled = null;

            for (GeoPoint sampled : sampledWithElevation) {
                double dist = routePoint.distanceToAsDouble(sampled);
                if (dist < minDist) {
                    minDist = dist;
                    nearestSampled = sampled;
                }
            }

            // Create new point with interpolated elevation
            double elevation = (nearestSampled != null && nearestSampled.getAltitude() != 0.0) ?
                    nearestSampled.getAltitude() : 0.0;

            GeoPoint newPoint = new GeoPoint(
                    routePoint.getLatitude(),
                    routePoint.getLongitude(),
                    elevation
            );
            result.add(newPoint);
        }

        return result;
    }
}