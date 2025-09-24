package be.kuleuven.gt.grvlfinder;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OverpassService {
    private static final String TAG = "OverpassService";
    private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface OverpassCallback {
        void onPreExecute();
        void onSuccess(List<PolylineResult> results);
        void onError(String error);
    }

    public static void fetchData(BoundingBox bbox, ScoreCalculator scoreCalculator,
                                 BikeTypeManager bikeTypeManager, OverpassCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        if (callback != null) {
            mainHandler.post(callback::onPreExecute);
        }

        executor.execute(() -> {
            try {
                // Step 1: Fetch OSM road data (no elevations yet)
                Log.d(TAG, "Fetching OSM road data...");
                List<PolylineResult> results = fetchDataSync(bbox, scoreCalculator);

                if (results.isEmpty()) {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onSuccess(new ArrayList<>());
                    });
                    return;
                }

                Log.d(TAG, "Found " + results.size() + " roads");

                // Step 2: Decide whether to fetch elevation data based on bike type
                boolean shouldFetchElevation = bikeTypeManager != null &&
                        bikeTypeManager.shouldFetchElevationData();

                if (shouldFetchElevation) {
                    Log.d(TAG, "Fetching elevation data for current bike mode...");

                    // Add slope data using strategic elevation sampling
                    ElevationService.addSlopeDataToRoads(results, new ElevationService.RoadElevationCallback() {
                        @Override
                        public void onSuccess(List<PolylineResult> updatedResults) {
                            Log.d(TAG, "Recalculating scores with accurate slope data...");

                            // CRITICAL: Recalculate scores using the new slope data
                            for (PolylineResult road : updatedResults) {
                                double maxSlope = road.getMaxSlopePercent();

                                if (maxSlope >= 0) { // Valid slope data
                                    int oldScore = road.getScore();
                                    int newScore = scoreCalculator.calculateScoreWithSlope(
                                            road.getTags(),
                                            road.getPoints(),
                                            maxSlope
                                    );
                                    road.setScore(newScore);

                                    Log.d(TAG, String.format("Road slope %.1f%% - Score: %d -> %d",
                                            maxSlope, oldScore, newScore));

                                    // Extra debug for steep roads
                                    if (maxSlope > 12.0) {
                                        Log.w(TAG, String.format("STEEP ROAD: %.1f%% slope, final score: %d",
                                                maxSlope, newScore));
                                    }
                                } else {
                                    Log.d(TAG, "Road has no slope data, keeping original score: " + road.getScore());
                                }
                            }

                            // Final sort by score
                            Collections.sort(updatedResults, (a, b) -> Integer.compare(b.getScore(), a.getScore()));

                            mainHandler.post(() -> {
                                if (callback != null) {
                                    Log.d(TAG, "Completed processing " + updatedResults.size() + " roads with elevation data");
                                    callback.onSuccess(updatedResults);
                                }
                            });
                        }

                        @Override
                        public void onError(String error) {
                            Log.w(TAG, "Elevation processing failed: " + error + ". Continuing without elevation data.");

                            // Continue without elevation data - just sort by current scores
                            Collections.sort(results, (a, b) -> Integer.compare(b.getScore(), a.getScore()));

                            mainHandler.post(() -> {
                                if (callback != null) {
                                    callback.onSuccess(results);
                                }
                            });
                        }
                    });

                } else {
                    Log.d(TAG, "Skipping elevation data fetch for current bike mode");

                    // Just sort by current scores without elevation processing
                    Collections.sort(results, (a, b) -> Integer.compare(b.getScore(), a.getScore()));

                    mainHandler.post(() -> {
                        if (callback != null) {
                            Log.d(TAG, "Completed processing " + results.size() + " roads without elevation data");
                            callback.onSuccess(results);
                        }
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Error processing roads", e);
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onError("Error: " + e.getMessage());
                    }
                });
            }
        });
    }

    private static List<PolylineResult> fetchDataSync(BoundingBox bbox, ScoreCalculator scoreCalculator) throws Exception {
        String bboxStr = bbox.getLatSouth() + "," + bbox.getLonWest() + "," +
                bbox.getLatNorth() + "," + bbox.getLonEast();

        String query = "[out:json][timeout:20];(" +
                "way[\"surface\"](" + bboxStr + ");" +
                "way[\"tracktype\"](" + bboxStr + ");" +
                "way[\"smoothness\"](" + bboxStr + ");" +
                "way[\"bicycle\"](" + bboxStr + ");" +
                "way[\"incline\"](" + bboxStr + ");" +
                "way[\"highway\"~\"track|unclassified|service|residential|cycleway\"](" + bboxStr + ");" +
                ");out body geom;";

        return executeQuery(query, scoreCalculator);
    }

    private static List<PolylineResult> executeQuery(String query, ScoreCalculator scoreCalculator) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(OVERPASS_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(12000);

            byte[] out = query.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(out.length);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.connect();

            try (OutputStream os = conn.getOutputStream()) {
                os.write(out);
            }

            int status = conn.getResponseCode();
            InputStream is = (status >= 200 && status < 400) ?
                    conn.getInputStream() : conn.getErrorStream();

            if (is == null) {
                throw new Exception("No response from Overpass server");
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }

            return parseJsonResponse(sb.toString(), scoreCalculator);

        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static List<PolylineResult> parseJsonResponse(String jsonText, ScoreCalculator scoreCalculator) throws Exception {
        JSONObject root = new JSONObject(jsonText);
        JSONArray elements = root.optJSONArray("elements");
        if (elements == null) return new ArrayList<>();

        List<PolylineResult> results = new ArrayList<>();

        for (int i = 0; i < elements.length(); i++) {
            JSONObject el = elements.getJSONObject(i);
            if (!"way".equals(el.optString("type", ""))) continue;

            Map<String, String> tagsMap = extractTags(el.optJSONObject("tags"));
            List<GeoPoint> points = extractGeometry(el.optJSONArray("geometry"));

            if (points.size() < 2) continue;

            // Set initial elevation to 0 - will be updated by ElevationService
            for (GeoPoint point : points) {
                point.setAltitude(0.0);
            }

            // Calculate initial score without elevation data (slope scoring will be 0)
            int score = scoreCalculator.calculateScore(tagsMap, points);
            results.add(new PolylineResult(points, score, tagsMap));
        }

        Log.d(TAG, "Parsed " + results.size() + " roads from OSM data");
        return results;
    }

    private static Map<String, String> extractTags(JSONObject tags) throws Exception {
        Map<String, String> tagsMap = new HashMap<>();
        if (tags != null && tags.names() != null) {
            for (int t = 0; t < tags.names().length(); t++) {
                String key = tags.names().getString(t);
                tagsMap.put(key, tags.optString(key));
            }
        }
        return tagsMap;
    }

    private static List<GeoPoint> extractGeometry(JSONArray geom) throws Exception {
        List<GeoPoint> points = new ArrayList<>();
        if (geom != null) {
            for (int p = 0; p < geom.length(); p++) {
                JSONObject gpt = geom.getJSONObject(p);
                GeoPoint point = new GeoPoint(gpt.getDouble("lat"), gpt.getDouble("lon"));
                // Initialize with 0 elevation - will be updated later
                point.setAltitude(0.0);
                points.add(point);
            }
        }
        return points;
    }
}