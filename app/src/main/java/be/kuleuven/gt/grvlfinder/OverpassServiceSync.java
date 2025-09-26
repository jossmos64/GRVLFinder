package be.kuleuven.gt.grvlfinder;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Synchronous version of OverpassService for use in GPX analysis
 */
public class OverpassServiceSync {
    private static final String TAG = "OverpassServiceSync";
    private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter";

    public static List<PolylineResult> fetchDataSync(BoundingBox bbox, ScoreCalculator scoreCalculator) throws Exception {
        String bboxStr = bbox.getLatSouth() + "," + bbox.getLonWest() + "," +
                bbox.getLatNorth() + "," + bbox.getLonEast();

        String query = "[out:json][timeout:10];(" +
                "way[\"surface\"](" + bboxStr + ");" +
                "way[\"tracktype\"](" + bboxStr + ");" +
                "way[\"smoothness\"](" + bboxStr + ");" +
                "way[\"bicycle\"](" + bboxStr + ");" +
                "way[\"incline\"](" + bboxStr + ");" +
                "way[\"highway\"~\"track|unclassified|service|residential|cycleway\"](" + bboxStr + ");" +
                ");out body geom;";

        return executeQuerySync(query, scoreCalculator);
    }

    private static List<PolylineResult> executeQuerySync(String query, ScoreCalculator scoreCalculator) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(OVERPASS_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);

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

            return parseJsonResponseSync(sb.toString(), scoreCalculator);

        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static List<PolylineResult> parseJsonResponseSync(String jsonText, ScoreCalculator scoreCalculator) throws Exception {
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

            // Set initial elevation to 0 - will be updated by ElevationService if needed
            for (GeoPoint point : points) {
                point.setAltitude(0.0);
            }

            // Calculate initial score without elevation data
            int score = scoreCalculator.calculateScore(tagsMap, points);
            results.add(new PolylineResult(points, score, tagsMap));
        }

        Log.d(TAG, "Parsed " + results.size() + " roads from OSM data (sync)");
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
                // Initialize with 0 elevation - will be updated later if needed
                point.setAltitude(0.0);
                points.add(point);
            }
        }
        return points;
    }
}