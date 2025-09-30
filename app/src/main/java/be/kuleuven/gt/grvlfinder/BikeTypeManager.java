package be.kuleuven.gt.grvlfinder;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;

public class BikeTypeManager {

    private BikeType currentBikeType = BikeType.GRAVEL_BIKE; // Default
    private SharedPreferences prefs;
    private Map<String, Integer> customWeights = new HashMap<>();
    private boolean elevationDataEnabled = false; // Default disabled otherwise its slow by default

    public BikeTypeManager(SharedPreferences prefs) {
        this.prefs = prefs;
        loadFromPreferences();
        initializeCustomWeights();
    }

    private void initializeCustomWeights() {
        customWeights.put("surface", 10);
        customWeights.put("smoothness", 5);
        customWeights.put("tracktype", 10);
        customWeights.put("bicycle", 0);
        customWeights.put("width", 10);
        customWeights.put("length", 10);
        customWeights.put("slope", 10);
        loadCustomWeights();
    }

    private void loadCustomWeights() {
        for (String key : customWeights.keySet()) {
            int saved = prefs.getInt("custom_weight_" + key, customWeights.get(key));
            customWeights.put(key, saved);
        }
    }

    public void saveCustomWeights() {
        for (Map.Entry<String, Integer> entry : customWeights.entrySet()) {
            prefs.edit().putInt("custom_weight_" + entry.getKey(), entry.getValue()).apply();
        }
    }

    private void loadFromPreferences() {
        String savedType = prefs.getString("selected_bike_type", BikeType.GRAVEL_BIKE.name());
        try {
            currentBikeType = BikeType.valueOf(savedType);
        } catch (IllegalArgumentException e) {
            currentBikeType = BikeType.GRAVEL_BIKE; // Fallback because this is the main point of the app
        }

        // Load elevation data preference
        elevationDataEnabled = prefs.getBoolean("elevation_data_enabled", false);
    }

    public void setBikeType(BikeType bikeType) {
        this.currentBikeType = bikeType;
        prefs.edit().putString("selected_bike_type", bikeType.name()).apply();
    }

    public BikeType getCurrentBikeType() {

        return currentBikeType;
    }

    public Map<String, Integer> getCustomWeights() {

        return new HashMap<>(customWeights);
    }

    public void updateCustomWeight(String key, int value) {

        customWeights.put(key, value);
    }

    public boolean isElevationDataEnabled() {

        return elevationDataEnabled;
    }

    public void setElevationDataEnabled(boolean enabled) {
        this.elevationDataEnabled = enabled;
        prefs.edit().putBoolean("elevation_data_enabled", enabled).apply();
    }

    public Map<String, Integer> getCurrentWeights() {
        switch (currentBikeType) {
            case RACE_ROAD:
                return getRaceRoadWeights();
            case GRAVEL_BIKE:
                return getGravelBikeWeights();
            case RACE_BIKEPACKING:
                return getRaceBikepackingWeights();
            case GRAVEL_BIKEPACKING:
                return getGravelBikepackingWeights();
            case CUSTOM:
                return getCustomWeights();
            default:
                return getGravelBikeWeights();
        }
    }

    private Map<String, Integer> getRaceRoadWeights() {
        Map<String, Integer> weights = new HashMap<>();
        weights.put("surface", 30);    // High preference for good surfaces
        weights.put("smoothness", 10);  // Smoothness matters for racing
        weights.put("tracktype", -50);  // Avoid tracks
        weights.put("bicycle", 10);     // Bike access important
        weights.put("width", 5);       // Width somewhat important
        weights.put("length", 8);      // Longer segments preferred
        weights.put("slope", 0);       // No slope penalty for racing
        return weights;
    }

    private Map<String, Integer> getGravelBikeWeights() {
        Map<String, Integer> weights = new HashMap<>();
        weights.put("surface", 10);
        weights.put("smoothness", 5);
        weights.put("tracktype", 10);
        weights.put("bicycle", 0);
        weights.put("width", 10);
        weights.put("length", 10);
        weights.put("slope", 0);       // No slope penalty for gravel riding
        return weights;
    }

    private Map<String, Integer> getRaceBikepackingWeights() {
        Map<String, Integer> weights = new HashMap<>();
        weights.put("surface", 30);    // Good surfaces important for loaded bike
        weights.put("smoothness", 12);  // Smoothness matters with load
        weights.put("tracktype", -50);  // Slight preference against tracks
        weights.put("bicycle", 10);     // Bike access matters
        weights.put("width", 6);       // Width important with panniers
        weights.put("length", 5);      // Length less important for touring
        weights.put("slope", 10);      // Avoid steep slopes with heavy load
        return weights;
    }

    private Map<String, Integer> getGravelBikepackingWeights() {
        Map<String, Integer> weights = new HashMap<>();
        weights.put("surface", 10);
        weights.put("smoothness", 5);
        weights.put("tracktype", 10);
        weights.put("bicycle", 0);
        weights.put("width", 10);
        weights.put("length", 10);
        weights.put("slope", 10);      // Full slope penalty for loaded gravel touring
        return weights;
    }

    public boolean shouldPenalizeSlopes() {
        switch (currentBikeType) {
            case RACE_ROAD:
            case GRAVEL_BIKE:
                return false;
            case RACE_BIKEPACKING:
            case GRAVEL_BIKEPACKING:
                return true;
            case CUSTOM:
                return getCurrentWeights().get("slope") > 0;
            default:
                return false;
        }
    }

    public boolean shouldFetchElevationData() {
        // For bikepacking modes, always fetch elevation data (regardless of setting), takes longer but is very important
        if (currentBikeType == BikeType.RACE_BIKEPACKING ||
                currentBikeType == BikeType.GRAVEL_BIKEPACKING) {
            return true;
        }

        // For race road and gravel bike, use the user setting
        if (currentBikeType == BikeType.RACE_ROAD ||
                currentBikeType == BikeType.GRAVEL_BIKE) {
            return elevationDataEnabled;
        }

        // For custom mode, check if slope weight > 0 and setting enabled
        if (currentBikeType == BikeType.CUSTOM) {
            return elevationDataEnabled && getCurrentWeights().get("slope") > 0;
        }

        return elevationDataEnabled; // Default
    }

    public boolean prefersPavedRoads() {
        switch (currentBikeType) {
            case RACE_ROAD:
            case RACE_BIKEPACKING:
                return true;
            case GRAVEL_BIKE:
            case GRAVEL_BIKEPACKING:
            case CUSTOM:
            default:
                return false;
        }
    }
}