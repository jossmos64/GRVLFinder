package be.kuleuven.gt.grvlfinder;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Enhanced Weather service with regional support for viewport-based analysis
 */
public class WeatherService {
    private static final String TAG = "WeatherService";
    private static final String OPEN_METEO_URL = "https://api.open-meteo.com/v1/forecast";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Thresholds
    private static final double RAIN_THRESHOLD_MM = 5.0;
    private static final int DAYS_TO_ANALYZE = 7;
    private static final int MIN_RAINY_DAYS = 2;

    public interface WeatherCallback {
        void onWeatherDataReceived(WeatherCondition condition);
        void onError(String error);
    }

    public static class WeatherCondition {
        public int rainyDaysCount;
        public double totalPrecipitationMm;
        public boolean isMuddy;
        public String warningMessage;
        public List<DailyWeather> dailyData;
        public GeoPoint location; // Track which location this is for

        public WeatherCondition() {
            this.dailyData = new ArrayList<>();
        }

        public String getDetailedReport() {
            StringBuilder report = new StringBuilder();
            report.append(String.format("Past %d days:\n", DAYS_TO_ANALYZE));
            report.append(String.format("• Rainy days: %d\n", rainyDaysCount));
            report.append(String.format("• Total precipitation: %.1f mm\n", totalPrecipitationMm));

            if (isMuddy) {
                report.append("\n⚠️ Warning: Unpaved roads may be muddy");
            }

            return report.toString();
        }
    }

    public static class DailyWeather {
        public String date;
        public double precipitationMm;
        public boolean isRainy;

        public DailyWeather(String date, double precipitation) {
            this.date = date;
            this.precipitationMm = precipitation;
            this.isRainy = precipitation >= RAIN_THRESHOLD_MM;
        }
    }

    /**
     * Fetch weather for current user location
     */
    public static void fetchWeatherCondition(GeoPoint location, WeatherCallback callback) {
        if (location == null) {
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("No location provided"));
            }
            return;
        }

        fetchWeatherForPoint(location, callback);
    }

    /**
     * Fetch weather for the center of a bounding box (viewport)
     */
    public static void fetchWeatherForViewport(BoundingBox bbox, WeatherCallback callback) {
        if (bbox == null) {
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("No viewport provided"));
            }
            return;
        }

        // Get center point of viewport
        double centerLat = (bbox.getLatNorth() + bbox.getLatSouth()) / 2.0;
        double centerLon = (bbox.getLonEast() + bbox.getLonWest()) / 2.0;
        GeoPoint centerPoint = new GeoPoint(centerLat, centerLon);

        Log.d(TAG, "Fetching weather for viewport center: " + centerLat + ", " + centerLon);
        fetchWeatherForPoint(centerPoint, callback);
    }

    /**
     * Core weather fetching logic
     */
    private static void fetchWeatherForPoint(GeoPoint location, WeatherCallback callback) {
        Log.d(TAG, "Fetching weather data for: " + location.getLatitude() + ", " + location.getLongitude());

        executor.execute(() -> {
            try {
                // Calculate date range
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                Calendar cal = Calendar.getInstance();
                String endDate = dateFormat.format(cal.getTime());

                cal.add(Calendar.DAY_OF_YEAR, -DAYS_TO_ANALYZE);
                String startDate = dateFormat.format(cal.getTime());

                // Build API URL
                String urlStr = String.format(Locale.US,
                        "%s?latitude=%.6f&longitude=%.6f&start_date=%s&end_date=%s&daily=precipitation_sum&timezone=auto",
                        OPEN_METEO_URL,
                        location.getLatitude(),
                        location.getLongitude(),
                        startDate,
                        endDate);

                Log.d(TAG, "Weather API URL: " + urlStr);

                // Fetch data
                String response = fetchUrl(urlStr);

                // Parse response
                WeatherCondition condition = parseWeatherResponse(response);
                condition.location = location;

                // Determine if conditions are muddy
                condition.isMuddy = condition.rainyDaysCount >= MIN_RAINY_DAYS;

                if (condition.isMuddy) {
                    condition.warningMessage = String.format(
                            "⚠️ Recent rain (%d days): Unpaved roads may be muddy",
                            condition.rainyDaysCount);
                } else {
                    condition.warningMessage = "✓ Good conditions: Limited recent rain";
                }

                Log.d(TAG, String.format("Weather analysis: %d rainy days, %.1f mm total, muddy=%b",
                        condition.rainyDaysCount, condition.totalPrecipitationMm, condition.isMuddy));

                // Return on main thread
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onWeatherDataReceived(condition));
                }

            } catch (Exception e) {
                Log.e(TAG, "Error fetching weather data", e);
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onError("Weather fetch failed: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * Fetch URL content
     */
    private static String fetchUrl(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "GRVLFinder-Android/1.0");

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new Exception("HTTP " + responseCode);
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            return response.toString();

        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Parse Open-Meteo JSON response
     */
    private static WeatherCondition parseWeatherResponse(String jsonStr) throws Exception {
        WeatherCondition condition = new WeatherCondition();

        JSONObject root = new JSONObject(jsonStr);
        JSONObject daily = root.getJSONObject("daily");

        JSONArray dates = daily.getJSONArray("time");
        JSONArray precipitation = daily.getJSONArray("precipitation_sum");

        for (int i = 0; i < dates.length(); i++) {
            String date = dates.getString(i);
            double precip = precipitation.isNull(i) ? 0.0 : precipitation.getDouble(i);

            DailyWeather day = new DailyWeather(date, precip);
            condition.dailyData.add(day);

            condition.totalPrecipitationMm += precip;

            if (day.isRainy) {
                condition.rainyDaysCount++;
            }
        }

        return condition;
    }

    /**
     * Calculate weather-based score penalty for roads
     * Returns negative value to penalize muddy conditions on unpaved roads
     */
    public static int calculateWeatherScorePenalty(WeatherCondition condition,
                                                   String surfaceType) {
        if (condition == null || !condition.isMuddy) {
            return 0; // No penalty
        }

        if (surfaceType == null) return 0;

        String surface = surfaceType.toLowerCase();

        // Dirt/unpaved roads get heavy penalty when muddy
        if (surface.contains("dirt") || surface.contains("ground") ||
                surface.contains("earth") || surface.contains("unpaved")) {
            // Scale penalty based on rainy days (3-7 days)
            int excessDays = condition.rainyDaysCount - MIN_RAINY_DAYS;
            return -15 - (excessDays * 5); // -15 to -35 penalty
        }

        // Gravel roads get moderate penalty when muddy
        if (surface.contains("gravel") || surface.contains("compacted")) {
            int excessDays = condition.rainyDaysCount - MIN_RAINY_DAYS;
            return -8 - (excessDays * 2); // -8 to -16 penalty
        }

        // Paved roads not affected
        return 0;
    }

    /**
     * Get a user-friendly warning message for specific road type
     */
    public static String getWarningForSurface(WeatherCondition condition, String surfaceType) {
        if (condition == null || !condition.isMuddy) {
            return null;
        }

        if (surfaceType == null) return null;

        String surface = surfaceType.toLowerCase();

        if (surface.contains("dirt") || surface.contains("ground") ||
                surface.contains("earth") || surface.contains("unpaved")) {
            return String.format("⚠️ High mud risk - %d rainy days recently",
                    condition.rainyDaysCount);
        }

        if (surface.contains("gravel") || surface.contains("compacted")) {
            return String.format("⚠️ Moderate mud risk - %d rainy days recently",
                    condition.rainyDaysCount);
        }

        return null;
    }
}