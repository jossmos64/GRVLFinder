package be.kuleuven.gt.grvlfinder;

import android.util.Log;
import org.osmdroid.util.GeoPoint;
import java.util.List;
import java.util.Map;

/**
 * Enhanced score calculator that considers weather conditions
 * Extends the base ScoreCalculator with weather-aware adjustments
 */
public class WeatherAwareScoreCalculator extends ScoreCalculator {
    private static final String TAG = "WeatherAwareScoreCalc";
    private WeatherService.WeatherCondition currentWeatherCondition;
    private boolean weatherDataEnabled = true;

    public WeatherAwareScoreCalculator(Map<String, Integer> weights) {
        super(weights);
    }

    /**
     * Set current weather condition for scoring
     */
    public void setWeatherCondition(WeatherService.WeatherCondition condition) {
        this.currentWeatherCondition = condition;
        if (condition != null) {
            Log.d(TAG, "Weather condition set: " + condition.rainyDaysCount +
                    " rainy days, muddy=" + condition.isMuddy);
        }
    }

    /**
     * Enable/disable weather-based scoring
     */
    public void setWeatherDataEnabled(boolean enabled) {

        this.weatherDataEnabled = enabled;
    }

    public boolean isWeatherDataEnabled() {
        return weatherDataEnabled;
    }

    public WeatherService.WeatherCondition getCurrentWeatherCondition() {
        return currentWeatherCondition;
    }

    /**
     * Calculate score with weather adjustments
     */
    @Override
    public int calculateScore(Map<String, String> tags, List<GeoPoint> points) {
        // Get base score from parent class
        int baseScore = super.calculateScore(tags, points);

        // Apply weather penalty if enabled and data available
        if (weatherDataEnabled && currentWeatherCondition != null && currentWeatherCondition.isMuddy) {
            String surface = tags.get("surface");
            int weatherPenalty = WeatherService.calculateWeatherScorePenalty(
                    currentWeatherCondition, surface);

            if (weatherPenalty < 0) {
                Log.d(TAG, String.format("Weather penalty for %s: %d (rainy days: %d)",
                        surface, weatherPenalty, currentWeatherCondition.rainyDaysCount));
            }

            return Math.max(0, baseScore + weatherPenalty);
        }

        return baseScore;
    }

    /**
     * Calculate score with slope and weather adjustments
     */
    @Override
    public int calculateScoreWithSlope(Map<String, String> tags, List<GeoPoint> points,
                                       double maxSlopePercent) {
        // Get base score with slope from parent class
        int baseScore = super.calculateScoreWithSlope(tags, points, maxSlopePercent);

        // Apply weather penalty if enabled and data available
        if (weatherDataEnabled && currentWeatherCondition != null && currentWeatherCondition.isMuddy) {
            String surface = tags.get("surface");
            int weatherPenalty = WeatherService.calculateWeatherScorePenalty(
                    currentWeatherCondition, surface);

            if (weatherPenalty < 0) {
                Log.d(TAG, String.format("Weather penalty for %s with %.1f%% slope: %d",
                        surface, maxSlopePercent, weatherPenalty));
            }

            return Math.max(0, baseScore + weatherPenalty);
        }

        return baseScore;
    }

    /**
     * Get weather warning for a specific road
     */
    public String getWeatherWarning(Map<String, String> tags) {
        if (!weatherDataEnabled || currentWeatherCondition == null || !currentWeatherCondition.isMuddy) {
            return null;
        }

        String surface = tags.get("surface");
        if (surface == null) return null;

        surface = surface.toLowerCase();

        if (surface.contains("dirt") || surface.contains("ground") ||
                surface.contains("earth") || surface.contains("unpaved")) {
            return "⚠️ May be very muddy - " + currentWeatherCondition.rainyDaysCount +
                    " rainy days recently";
        }

        if (surface.contains("gravel") || surface.contains("compacted")) {
            return "⚠️ May be muddy - " + currentWeatherCondition.rainyDaysCount +
                    " rainy days recently";
        }

        return null;
    }

    /**
     * Check if weather data is available and current
     */
    public boolean hasWeatherData() {

        return currentWeatherCondition != null;
    }

    /**
     * Get summary of current weather impact
     */
    public String getWeatherSummary() {
        if (currentWeatherCondition == null) {
            return "No weather data available";
        }

        if (!weatherDataEnabled) {
            return "Weather analysis disabled";
        }

        return currentWeatherCondition.getDetailedReport();
    }
}