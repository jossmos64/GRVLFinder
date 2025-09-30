package be.kuleuven.gt.grvlfinder;

import org.osmdroid.util.GeoPoint;
import android.util.Log;

import java.util.List;
import java.util.Map;

public class ScoreCalculator {
    private static final String TAG = "ScoreCalculator";
    private Map<String, Integer> weights;
    private BikeTypeManager bikeTypeManager;

    public ScoreCalculator(Map<String, Integer> weights) {
        this.weights = weights;
    }

    public void setBikeTypeManager(BikeTypeManager bikeTypeManager) {
        this.bikeTypeManager = bikeTypeManager;
    }

    public int calculateScore(Map<String, String> tags, List<GeoPoint> points) {
        // Use bike type manager weights if available, otherwise fall back to instance weights
        Map<String, Integer> currentWeights = (bikeTypeManager != null) ?
                bikeTypeManager.getCurrentWeights() : weights;

        return calculateScoreWithWeights(tags, points, currentWeights);
    }

    private int calculateScoreWithWeights(Map<String, String> tags, List<GeoPoint> points,
                                          Map<String, Integer> currentWeights) {
        int score = 0;

        int w_surface = currentWeights.getOrDefault("surface", 3);
        int w_smoothness = currentWeights.getOrDefault("smoothness", 2);
        int w_tracktype = currentWeights.getOrDefault("tracktype", 2);
        int w_bicycle = currentWeights.getOrDefault("bicycle", 2);
        int w_width = currentWeights.getOrDefault("width", 1);
        int w_length = currentWeights.getOrDefault("length", 1);
        int w_slope = currentWeights.getOrDefault("slope", 5);

        // Apply bike-type specific scoring
        if (bikeTypeManager != null && bikeTypeManager.prefersPavedRoads()) {
            score += calculateRoadSurfaceScore(tags.get("surface"), w_surface);
        } else {
            score += calculateSurfaceScore(tags.get("surface"), w_surface);
        }

        score += calculateSmoothnessScore(tags.get("smoothness"), w_smoothness);
        score += calculateTracktypeScore(tags.get("tracktype"), w_tracktype);
        score += calculateBicycleScore(tags.get("bicycle"), w_bicycle);
        score += calculateWidthScore(tags.get("width"), w_width);
        score += calculateLengthScore(points, w_length);
        score += calculateSlopeScore(tags, w_slope);

        return Math.max(0, score);
    }

    public int calculateScoreWithSlope(Map<String, String> tags, List<GeoPoint> points, double maxSlopePercent) {
        // Use bike type manager weights if available
        Map<String, Integer> currentWeights = (bikeTypeManager != null) ?
                bikeTypeManager.getCurrentWeights() : weights;

        int baseScore = calculateScoreWithWeights(tags, points, currentWeights);
        // Remove the slope score from tags (which might be inaccurate)
        int w_slope = currentWeights.getOrDefault("slope", 5);
        baseScore -= calculateSlopeScore(tags, w_slope);

        // Add the accurate slope score only if bike type uses slope penalties
        if (bikeTypeManager == null || bikeTypeManager.shouldPenalizeSlopes()) {
            baseScore += calculateAccurateSlopeScore(maxSlopePercent, w_slope);
        }

        return Math.max(0, baseScore);
    }

    private int calculateRoadSurfaceScore(String surface, int weight) {
        if (surface == null) return 0;
        switch (surface.toLowerCase()) {
            case "asphalt": case "paved": case "concrete": case "concrete:plates":
                return 3 * weight; // High score for paved surfaces
            case "compacted": case "fine_gravel":
                return 1 * weight; // Acceptable for road bikes
            case "gravel": case "pebblestone":
                return -1 * weight; // Slight penalty for gravel
            case "ground": case "earth": case "dirt": case "unpaved":
                return -3 * weight; // Strong penalty for unpaved
            default:
                return 0;
        }
    }

    private int calculateSurfaceScore(String surface, int weight) {
        if (surface == null) return 0;
        switch (surface.toLowerCase()) {
            case "gravel": case "fine_gravel": case "pebblestone": case "compacted":
                return 2 * weight;
            case "ground": case "earth": case "dirt": case "unpaved":
                return 1 * weight;
            case "asphalt": case "paved": case "concrete": case "concrete:plates":
                return -4 * weight;
            default:
                return 0;
        }
    }

    private int calculateSmoothnessScore(String smoothness, int weight) {
        if (smoothness == null) return 0;
        switch (smoothness.toLowerCase()) {
            case "good": return 1 * weight;
            case "bad": return -1 * weight;
            default: return 0;
        }
    }

    private int calculateTracktypeScore(String tracktype, int weight) {
        if (tracktype == null) return 0;
        switch (tracktype.toLowerCase()) {
            case "grade2": case "grade3": return weight;
            case "grade1": return -weight;
            default: return 0;
        }
    }

    private int calculateBicycleScore(String bicycle, int weight) {
        if (bicycle == null) return 0;
        switch (bicycle.toLowerCase()) {
            case "yes": case "designated": return weight;
            case "no": return -2 * weight;
            default: return 0;
        }
    }

    private int calculateWidthScore(String width, int weight) {
        if (width == null) return 0;
        try {
            String numStr = width.replaceAll("[^0-9.]", "");
            double meters = Double.parseDouble(numStr);
            if (meters >= 3) return weight;
            else if (meters < 1.5) return -weight;
            return 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int calculateLengthScore(List<GeoPoint> points, int weight) {
        if (points == null || points.size() < 2) return 0;

        double length = 0;
        for (int i = 1; i < points.size(); i++) {
            length += points.get(i-1).distanceToAsDouble(points.get(i));
        }

        if (length >= 300) return weight;
        else if (length < 50) return -weight;
        return 0;
    }

    private int calculateSlopeScore(Map<String, String> tags, int weight) {
        if (weight == 0) return 0;

        String incline = tags.get("incline");
        if (incline == null) return 0; // No penalty for missing data

        try {
            // Parse incline value (can be "5%", "steep", "up", etc.)
            String cleaned = incline.toLowerCase().replaceAll("[^0-9.-]", "");
            if (cleaned.isEmpty()) {
                // Handle text values
                switch (incline.toLowerCase()) {
                    case "steep": return -weight * 200; // Much harsher penalty
                    case "up": case "down": return -weight * 50;
                    default: return 0;
                }
            }

            double slopePercent = Double.parseDouble(cleaned);
            return calculateSlopeScoreFromPercent(slopePercent, weight);

        } catch (NumberFormatException e) {
            Log.w(TAG, "Could not parse incline: " + incline);
            return 0;
        }
    }

    /**
     * Accurate slope scoring using calculated slope data
     */
    private int calculateAccurateSlopeScore(double maxSlopePercent, int weight) {
        if (maxSlopePercent < 0) {
            return 0; // No data, no penalty
        }

        return calculateSlopeScoreFromPercent(maxSlopePercent, weight);
    }

    private int calculateSlopeScoreFromPercent(double slopePercent, int weight) {
        // Use absolute value for consistent scoring
        double absSlope = Math.abs(slopePercent);

        // MASSIVELY increased penalties for slopes above 12%
        if (absSlope >= 20.0) {
            return -weight * 1000; // Essentially eliminates the road from consideration
        } else if (absSlope >= 15.0) {
            return -weight * 500;  // Extreme penalty - road will score very poorly
        } else if (absSlope >= 12.0) {
            return -weight * 250;  // Very harsh penalty - this is your main target
        } else {
            return 0;              // Gentle slope, no penalty
        }
    }

    @Deprecated
    public static double calculateMaxSlopePercent(List<GeoPoint> points) {
        Log.d(TAG, "calculateMaxSlopePercent is deprecated - slope is now calculated by ElevationService");
        return -1;
    }

    public void updateWeights(Map<String, Integer> newWeights) {
        this.weights.clear();
        this.weights.putAll(newWeights);

        // If we have a bike type manager, update its custom weights if in custom mode
        if (bikeTypeManager != null && bikeTypeManager.getCurrentBikeType() == BikeType.CUSTOM) {
            for (Map.Entry<String, Integer> entry : newWeights.entrySet()) {
                bikeTypeManager.updateCustomWeight(entry.getKey(), entry.getValue());
            }
            bikeTypeManager.saveCustomWeights();
        }
    }
}