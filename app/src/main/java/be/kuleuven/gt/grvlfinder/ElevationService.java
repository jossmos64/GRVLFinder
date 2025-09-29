package be.kuleuven.gt.grvlfinder;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ElevationService {
    private static final String TAG = "ElevationService";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface RoadElevationCallback {
        void onSuccess(List<PolylineResult> updatedResults);
        void onError(String error);
    }

    /**
     * Add slope data to roads using strategic elevation sampling
     * UPDATED: Now also sets altitude on GeoPoints for GPX export
     */
    public static void addSlopeDataToRoads(List<PolylineResult> roads, RoadElevationCallback callback) {
        if (roads == null || roads.isEmpty()) {
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("No roads provided"));
            }
            return;
        }

        Log.d(TAG, "Analyzing slope data for " + roads.size() + " roads using strategic sampling");

        executor.execute(() -> {
            try {
                // Create elevation requests for all roads
                List<RoadElevationRequest> requests = new ArrayList<>();
                List<GeoPoint> allElevationPoints = new ArrayList<>();

                for (int i = 0; i < roads.size(); i++) {
                    PolylineResult road = roads.get(i);
                    RoadElevationRequest request = createElevationRequest(road, i);
                    requests.add(request);
                    allElevationPoints.addAll(request.elevationPoints);
                }

                Log.d(TAG, "Fetching elevation for " + allElevationPoints.size() + " strategic points");

                // Fetch elevations for all points at once
                List<Double> elevations = fetchElevationsInBatches(allElevationPoints);

                // Process results back to roads
                int elevationIndex = 0;
                for (RoadElevationRequest request : requests) {
                    PolylineResult road = roads.get(request.roadIndex);

                    // Extract elevations for this road
                    List<Double> roadElevations = new ArrayList<>();
                    for (int j = 0; j < request.elevationPoints.size() && elevationIndex < elevations.size(); j++) {
                        roadElevations.add(elevations.get(elevationIndex++));
                    }

                    // Calculate max slope for this road
                    double maxSlope = calculateRoadMaxSlope(request, roadElevations);
                    road.setMaxSlope(maxSlope);

                    // CRITICAL FIX: Set altitude on all points in the road
                    setAltitudesOnRoad(road.getPoints(), request.elevationPoints, roadElevations);

                    Log.d(TAG, "Road " + request.roadIndex + ": calculated max slope = " + maxSlope + "%");
                }

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (callback != null) {
                        Log.d(TAG, "Successfully calculated slope data and set elevations for all roads");
                        callback.onSuccess(roads);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error calculating road slopes: " + e.getMessage(), e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (callback != null) {
                        callback.onError("Failed to calculate slopes: " + e.getMessage());
                    }
                });
            }
        });
    }

    /**
     * NEW: Set altitude values on all road points by interpolating from sampled elevations
     */
    private static void setAltitudesOnRoad(List<GeoPoint> roadPoints,
                                           List<GeoPoint> sampledPoints,
                                           List<Double> sampledElevations) {
        if (sampledPoints.size() != sampledElevations.size()) {
            Log.w(TAG, "Mismatch between sampled points and elevations");
            return;
        }

        // For each road point, find nearest sampled point and use its elevation
        for (GeoPoint roadPoint : roadPoints) {
            double minDist = Double.MAX_VALUE;
            double closestElevation = 0.0;

            for (int i = 0; i < sampledPoints.size(); i++) {
                double dist = roadPoint.distanceToAsDouble(sampledPoints.get(i));
                if (dist < minDist) {
                    minDist = dist;
                    closestElevation = sampledElevations.get(i);
                }
            }

            // Set the altitude on this point
            roadPoint.setAltitude(closestElevation);
        }

        Log.d(TAG, "Set altitude on " + roadPoints.size() + " points");
    }

    /**
     * Create an elevation request for a single road
     */
    private static RoadElevationRequest createElevationRequest(PolylineResult road, int roadIndex) {
        List<GeoPoint> roadPoints = road.getPoints();
        double totalDistance = calculateTotalDistance(roadPoints);

        RoadElevationRequest request = new RoadElevationRequest();
        request.roadIndex = roadIndex;
        request.totalDistance = totalDistance;
        request.elevationPoints = new ArrayList<>();
        request.segmentDistances = new ArrayList<>();

        // Strategy: Sample elevation points optimally based on road length
        int targetSamples;
        double sampleInterval;

        if (totalDistance < 100) {
            targetSamples = 3;
        } else if (totalDistance < 500) {
            targetSamples = Math.max(4, (int)(totalDistance / 75));
        } else {
            targetSamples = Math.min(10, (int)(totalDistance / 100));
        }

        sampleInterval = totalDistance / (targetSamples - 1);

        Log.d(TAG, "Road " + roadIndex + ": " + totalDistance + "m, " + targetSamples + " samples, " + sampleInterval + "m intervals");

        // Create sample points along the road
        double currentDistance = 0.0;
        double nextSampleDistance = 0.0;

        for (int i = 0; i < roadPoints.size() - 1; i++) {
            GeoPoint p1 = roadPoints.get(i);
            GeoPoint p2 = roadPoints.get(i + 1);
            double segmentDistance = calculateDistance(p1, p2);
            double segmentStart = currentDistance;
            double segmentEnd = currentDistance + segmentDistance;

            while (nextSampleDistance <= segmentEnd && request.elevationPoints.size() < targetSamples) {
                if (nextSampleDistance <= segmentStart) {
                    request.elevationPoints.add(p1);
                    request.segmentDistances.add(nextSampleDistance);
                } else {
                    double ratio = (nextSampleDistance - segmentStart) / segmentDistance;
                    double lat = p1.getLatitude() + ratio * (p2.getLatitude() - p1.getLatitude());
                    double lon = p1.getLongitude() + ratio * (p2.getLongitude() - p1.getLongitude());

                    GeoPoint samplePoint = new GeoPoint(lat, lon);
                    request.elevationPoints.add(samplePoint);
                    request.segmentDistances.add(nextSampleDistance);
                }

                nextSampleDistance += sampleInterval;
            }

            currentDistance += segmentDistance;
        }

        // Always include the last point
        if (request.elevationPoints.size() < targetSamples ||
                !request.elevationPoints.get(request.elevationPoints.size() - 1).equals(roadPoints.get(roadPoints.size() - 1))) {
            request.elevationPoints.add(roadPoints.get(roadPoints.size() - 1));
            request.segmentDistances.add(totalDistance);
        }

        Log.d(TAG, "Created " + request.elevationPoints.size() + " elevation sample points for road " + roadIndex);
        return request;
    }

    /**
     * Calculate maximum slope for a road using elevation data
     */
    private static double calculateRoadMaxSlope(RoadElevationRequest request, List<Double> elevations) {
        if (elevations.size() < 2) {
            Log.w(TAG, "Not enough elevation data for slope calculation");
            return -1;
        }

        List<Double> segmentSlopes = new ArrayList<>();

        for (int i = 1; i < elevations.size() && i < request.segmentDistances.size(); i++) {
            double elevation1 = elevations.get(i - 1);
            double elevation2 = elevations.get(i);
            double distance1 = request.segmentDistances.get(i - 1);
            double distance2 = request.segmentDistances.get(i);

            double horizontalDistance = distance2 - distance1;
            double elevationDiff = Math.abs(elevation2 - elevation1);

            if (Double.isNaN(elevation1) || Double.isNaN(elevation2)) {
                Log.w(TAG, "NaN elevation values, skipping segment");
                continue;
            }

            if (elevation1 < -100 || elevation1 > 5000 || elevation2 < -100 || elevation2 > 5000) {
                Log.w(TAG, "Suspicious elevation values: " + elevation1 + ", " + elevation2);
                continue;
            }

            if (horizontalDistance < 20) {
                Log.d(TAG, "Segment too short: " + horizontalDistance + "m, skipping");
                continue;
            }

            double slopePercent = (elevationDiff / horizontalDistance) * 100.0;

            if (slopePercent >= 0 && slopePercent <= 40) {
                segmentSlopes.add(slopePercent);
                Log.d(TAG, String.format("Segment %.0f-%.0fm: %.1f%% slope (%.1fm rise over %.1fm)",
                        distance1, distance2, slopePercent, elevationDiff, horizontalDistance));
            } else {
                Log.w(TAG, "Slope out of range: " + slopePercent + "%");
            }
        }

        if (segmentSlopes.isEmpty()) {
            Log.w(TAG, "No valid slopes calculated");
            return -1;
        }

        segmentSlopes.sort(Double::compareTo);
        double maxSlope = segmentSlopes.get(segmentSlopes.size() - 1);

        Log.d(TAG, "All slopes: " + segmentSlopes + " -> Max: " + maxSlope + "%");

        return Math.min(35.0, maxSlope);
    }

    /**
     * Fetch elevations in batches with rate limiting
     */
    private static List<Double> fetchElevationsInBatches(List<GeoPoint> points) throws Exception {
        List<Double> allElevations = new ArrayList<>();
        int batchSize = 10;

        for (int start = 0; start < points.size(); start += batchSize) {
            int end = Math.min(start + batchSize, points.size());
            List<GeoPoint> batch = points.subList(start, end);

            Log.d(TAG, "Fetching elevation batch " + (start/batchSize + 1) + " of " +
                    ((points.size() + batchSize - 1)/batchSize));

            try {
                List<Double> batchElevations = fetchBatchElevations(batch);
                allElevations.addAll(batchElevations);
            } catch (Exception e) {
                Log.w(TAG, "Batch failed: " + e.getMessage() + ". Using defaults.");
                for (int i = 0; i < batch.size(); i++) {
                    allElevations.add(100.0);
                }
            }

            if (end < points.size()) {
                try {
                    Thread.sleep(600);
                } catch (InterruptedException ignored) {}
            }
        }

        return allElevations;
    }

    /**
     * Fetch elevations for a single batch
     */
    private static List<Double> fetchBatchElevations(List<GeoPoint> batch) throws Exception {
        StringBuilder locations = new StringBuilder();
        for (int i = 0; i < batch.size(); i++) {
            if (i > 0) locations.append("|");
            GeoPoint point = batch.get(i);
            locations.append(String.format(java.util.Locale.US, "%.6f,%.6f",
                    point.getLatitude(), point.getLongitude()));
        }

        String apiUrl = "https://api.opentopodata.org/v1/srtm30m";
        String fullUrl = apiUrl + "?locations=" + locations.toString();

        HttpURLConnection conn = null;
        try {
            URL url = new URL(fullUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", "GRVLFinder-Android/1.0");

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new Exception("API returned HTTP " + responseCode);
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            return parseElevationResponse(response.toString(), batch.size());

        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Parse elevation API response
     */
    private static List<Double> parseElevationResponse(String responseText, int expectedCount) throws Exception {
        JSONObject responseJson = new JSONObject(responseText);
        String status = responseJson.getString("status");

        if (!"OK".equals(status)) {
            throw new Exception("API status: " + status);
        }

        JSONArray results = responseJson.getJSONArray("results");
        List<Double> elevations = new ArrayList<>();

        for (int i = 0; i < results.length(); i++) {
            JSONObject result = results.getJSONObject(i);
            if (result.has("elevation") && !result.isNull("elevation")) {
                double elevation = result.getDouble("elevation");
                elevations.add(elevation);
            } else {
                elevations.add(100.0);
            }
        }

        while (elevations.size() < expectedCount) {
            elevations.add(100.0);
        }

        return elevations;
    }

    /**
     * Calculate total distance of a road
     */
    private static double calculateTotalDistance(List<GeoPoint> points) {
        double total = 0.0;
        for (int i = 1; i < points.size(); i++) {
            total += calculateDistance(points.get(i-1), points.get(i));
        }
        return total;
    }

    /**
     * Calculate distance between two points
     */
    private static double calculateDistance(GeoPoint p1, GeoPoint p2) {
        double lat1Rad = Math.toRadians(p1.getLatitude());
        double lat2Rad = Math.toRadians(p2.getLatitude());
        double deltaLatRad = Math.toRadians(p2.getLatitude() - p1.getLatitude());
        double deltaLonRad = Math.toRadians(p2.getLongitude() - p1.getLongitude());

        double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return 6371000 * c;
    }

    /**
     * Helper class for elevation requests
     */
    private static class RoadElevationRequest {
        int roadIndex;
        double totalDistance;
        List<GeoPoint> elevationPoints;
        List<Double> segmentDistances;
    }
}