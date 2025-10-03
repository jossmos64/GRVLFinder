package be.kuleuven.gt.grvlfinder;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends BaseMapActivity {

    private static final String TAG = "MainActivity";
    private static final double MAX_VIEWPORT_SPAN_DEG = 0.3;
    private static final long CACHE_TTL_MS = 60 * 1000;

    private Button findButton, exportButton, undoButton, criteriaButton, drawExploreButton, gpxAnalyzerButton;
    private Button weatherButton; // NEW
    private ProgressBar progressBar;

    private Map<String, Integer> weights = new HashMap<>();
    private WeatherAwareScoreCalculator scoreCalculator; // CHANGED from ScoreCalculator
    private FilterManager filterManager;
    private RouteManager routeManager;

    private List<Polyline> currentPolylines = new ArrayList<>();
    private BoundingBox lastBBoxQueried = null;
    private List<PolylineResult> lastResultsCache = null;
    private long lastQueryTimeMs = 0;

    private boolean isDrawingRoute = false;
    private boolean hasLoadedRoads = false;
    private Button bikeTypeButton;
    private BikeTypeManager bikeTypeManager;
    private View legendView;
    private LinearLayout weatherLegendView; // NEW
    private Button tutorialButton;

    private boolean hasLoadedWeatherOnce = false;
    private GeoPoint lastWeatherFetchLocation = null;
    private static final double WEATHER_REFETCH_DISTANCE_KM = 5.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeMap(findViewById(R.id.map));

        initializeWeights();
        scoreCalculator = new WeatherAwareScoreCalculator(weights);
        filterManager = new FilterManager();
        routeManager = new RouteManager(map);
        bikeTypeManager = new BikeTypeManager(prefs);

        scoreCalculator.setBikeTypeManager(bikeTypeManager);
        routeManager.setDependencies(bikeTypeManager, scoreCalculator);

        bindUI();
        setupEventHandlers();
        updateUIForBikeType();

        Button settingsButton = findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(v -> {
            startActivity(new android.content.Intent(MainActivity.this, SettingsActivity.class));
        });

        // ENHANCED: Fetch weather for initial viewport after map loads
        map.addMapListener(new org.osmdroid.events.MapListener() {
            @Override
            public boolean onScroll(org.osmdroid.events.ScrollEvent event) {
                // Optional: Fetch weather when user scrolls to new area
                // fetchWeatherForCurrentViewport();
                return false;
            }

            @Override
            public boolean onZoom(org.osmdroid.events.ZoomEvent event) {
                return false;
            }
        });

    }

    private void bindUI() {
        findButton = findViewById(R.id.findButton);
        exportButton = findViewById(R.id.exportButton);
        undoButton = findViewById(R.id.undoButton);
        criteriaButton = findViewById(R.id.criteriaButton);
        drawExploreButton = findViewById(R.id.drawExploreButton);
        progressBar = findViewById(R.id.progressBar);
        bikeTypeButton = findViewById(R.id.bikeTypeButton);
        gpxAnalyzerButton = findViewById(R.id.gpxAnalyzerButton);
        tutorialButton = findViewById(R.id.tutorialButton);
        weatherButton = findViewById(R.id.weatherButton);

        // NEW: Make OSM attribution clickable
        TextView osmAttribution = findViewById(R.id.osmAttribution);
        osmAttribution.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.openstreetmap.org/copyright"));
            startActivity(browserIntent);
        });

        LinearLayout legendContainer = findViewById(R.id.legendContainer);
        if (legendContainer != null) {
            legendView = LegendView.create(this);
            legendContainer.addView(legendView);
            LegendView.updateForBikeType(legendView, bikeTypeManager.getCurrentBikeType());

            // NEW: Add weather indicator to legend
            weatherLegendView = WeatherLegendView.create(this);
            legendContainer.addView(weatherLegendView);
        }

        View topButtons = findViewById(R.id.topButtonContainer);
        if (topButtons != null) {
            topButtons.bringToFront();
            topButtons.invalidate();
        }

        ToggleButton btnGreen = findViewById(R.id.btnGreen);
        ToggleButton btnYellow = findViewById(R.id.btnYellow);
        ToggleButton btnRed = findViewById(R.id.btnRed);

        filterManager.setButtons(btnGreen, btnYellow, btnRed);
        filterManager.setFilterCallback(() -> updateMapFilter());
    }

    private void setupEventHandlers() {
        findButton.setOnClickListener(v -> handleFindGravel());
        exportButton.setOnClickListener(v -> handleExportGpx());
        undoButton.setOnClickListener(v -> routeManager.undoLastSegment());
        bikeTypeButton.setOnClickListener(v -> showBikeTypeDialog());
        gpxAnalyzerButton.setOnClickListener(v -> openGpxAnalyzer());
        tutorialButton.setOnClickListener(v -> openTutorial());
        weatherButton.setOnClickListener(v -> showWeatherDialog()); // NEW

        if (criteriaButton != null) {
            criteriaButton.setOnClickListener(v ->
                    CriteriaSettingsDialog.show(this, weights));
        }

        drawExploreButton.setOnClickListener(v -> toggleDrawExploreMode());

        MapEventsReceiver mapReceiver = new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                if (isDrawingRoute) {
                    routeManager.addPointToRoute(p);
                }
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) { return false; }
        };
        map.getOverlays().add(new MapEventsOverlay(mapReceiver));
    }

    // NEW: Weather methods
    private void fetchWeatherForCurrentLocation() {
        if (weatherLegendView != null) {
            WeatherLegendView.setLoading(weatherLegendView, true);
        }

        // Wait for location to be available
        if (locationOverlay != null) {
            locationOverlay.runOnFirstFix(() -> {
                runOnUiThread(() -> {
                    GeoPoint location = locationOverlay.getMyLocation();
                    if (location != null) {
                        fetchWeatherForLocation(location);
                    }
                });
            });
        }
    }

    private void fetchWeatherForLocation(GeoPoint location) {
        Log.d(TAG, "Fetching weather for: " + location.getLatitude() + ", " + location.getLongitude());

        WeatherService.fetchWeatherCondition(location,
                new WeatherService.WeatherCallback() {
                    @Override
                    public void onWeatherDataReceived(WeatherService.WeatherCondition condition) {
                        // Set weather condition in calculator
                        scoreCalculator.setWeatherCondition(condition);

                        // Update legend
                        if (weatherLegendView != null) {
                            WeatherLegendView.updateWeatherStatus(weatherLegendView, condition);
                        }

                        // Show notification if muddy
                        if (condition.isMuddy) {
                            Toast.makeText(MainActivity.this,
                                    condition.warningMessage,
                                    Toast.LENGTH_LONG).show();
                        }

                        // Re-score existing roads if we have them
                        if (lastResultsCache != null && !lastResultsCache.isEmpty()) {
                            for (PolylineResult road : lastResultsCache) {
                                int newScore = scoreCalculator.calculateScore(
                                        road.getTags(),
                                        road.getPoints()
                                );
                                road.setScore(newScore);
                            }
                            updateMapFilter();
                        }

                        Log.d(TAG, "Weather updated: " + condition.rainyDaysCount +
                                " rainy days, muddy=" + condition.isMuddy);
                    }

                    @Override
                    public void onError(String error) {
                        Log.w(TAG, "Weather fetch failed: " + error);
                        if (weatherLegendView != null) {
                            WeatherLegendView.updateWeatherStatus(weatherLegendView, null);
                        }
                    }
                });
    }

    private void openGpxAnalyzer() {
        Intent intent = new Intent(this, GpxAnalyzerActivity.class);
        startActivity(intent);
    }

    private void toggleDrawExploreMode() {
        if (!hasLoadedRoads && !isDrawingRoute) {
            Toast.makeText(this, "Please use 'Find Gravel' first to load roads in this area", Toast.LENGTH_LONG).show();
            return;
        }

        LinearLayout bottomContainer = findViewById(R.id.bottomButtonContainer);

        if (!isDrawingRoute) {
            isDrawingRoute = true;
            drawExploreButton.setText("🔍");
            bottomContainer.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Route drawing activated - tap anywhere to draw", Toast.LENGTH_SHORT).show();

            if (lastResultsCache != null) {
                drawResults(filterManager.applyFilter(lastResultsCache));
            }
        } else {
            isDrawingRoute = false;
            drawExploreButton.setText("✏️");
            bottomContainer.setVisibility(View.GONE);
            routeManager.clearRoute();
            Toast.makeText(this, "Explore mode activated", Toast.LENGTH_SHORT).show();

            if (lastResultsCache != null) {
                drawResults(filterManager.applyFilter(lastResultsCache));
            }
        }
    }

    private String getLoadingMessage() {
        BikeType currentType = bikeTypeManager.getCurrentBikeType();
        switch (currentType) {
            case RACE_ROAD:
                return bikeTypeManager.shouldFetchElevationData() ?
                        "Searching for roads with elevation analysis..." : "Searching for roads...";
            case GRAVEL_BIKE:
                return bikeTypeManager.shouldFetchElevationData() ?
                        "Searching for gravel roads with elevation analysis..." : "Searching for gravel roads...";
            case RACE_BIKEPACKING:
                return "Searching for touring routes with elevation analysis...";
            case GRAVEL_BIKEPACKING:
                return "Searching for adventure routes with elevation analysis...";
            case CUSTOM:
                return bikeTypeManager.shouldFetchElevationData() ?
                        "Searching with custom criteria and elevation analysis..." : "Searching with custom criteria...";
            default:
                return "Searching...";
        }
    }

    private String getResultMessage(int resultCount) {
        BikeType currentType = bikeTypeManager.getCurrentBikeType();
        switch (currentType) {
            case RACE_ROAD:
                return "Found: " + resultCount + " roads";
            case GRAVEL_BIKE:
                return "Found: " + resultCount + " gravel roads";
            case RACE_BIKEPACKING:
                return "Found: " + resultCount + " touring routes";
            case GRAVEL_BIKEPACKING:
                return "Found: " + resultCount + " adventure routes";
            case CUSTOM:
                return "Found: " + resultCount + " routes (custom)";
            default:
                return "Found: " + resultCount + " roads";
        }
    }

    private void handleExportGpx() {
        if (!routeManager.hasRoute()) {
            Toast.makeText(this, "No route drawn", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_route_name, null);
        EditText input = dialogView.findViewById(R.id.routeNameInput);
        Button cancelBtn = dialogView.findViewById(R.id.cancelButton);
        Button saveBtn = dialogView.findViewById(R.id.saveButton);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        saveBtn.setOnClickListener(v -> {
            String routeName = input.getText().toString().trim();
            if (routeName.isEmpty()) {
                routeName = "route";
            }
            String filename = routeName.replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".gpx";

            progressBar.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Adding elevation data to route...", Toast.LENGTH_SHORT).show();

            GpxElevationHelper.addElevationToRoute(this, routeManager.getDrawnRoute(),
                    new GpxElevationHelper.ElevationCallback() {
                        @Override
                        public void onElevationAdded(List<GeoPoint> routeWithElevation) {
                            progressBar.setVisibility(View.GONE);

                            GpxExporter.exportRouteWithElevation(MainActivity.this, routeWithElevation, filename,
                                    new GpxExporter.ExportCallback() {
                                        @Override
                                        public void onSuccess(String message) {
                                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                                        }

                                        @Override
                                        public void onError(String error) {
                                            Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        }

                        @Override
                        public void onError(String error) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(MainActivity.this,
                                    "Could not fetch elevation data. Exporting without elevation.",
                                    Toast.LENGTH_LONG).show();

                            GpxExporter.exportRouteWithElevation(MainActivity.this, routeManager.getDrawnRoute(), filename,
                                    new GpxExporter.ExportCallback() {
                                        @Override
                                        public void onSuccess(String message) {
                                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                                        }

                                        @Override
                                        public void onError(String error) {
                                            Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        }
                    });

            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateMapFilter() {
        if (lastResultsCache == null) return;
        List<PolylineResult> filtered = filterManager.applyFilter(lastResultsCache);
        drawResults(filtered);
    }

    private void drawResults(List<PolylineResult> results) {
        for (Polyline p : currentPolylines) {
            map.getOverlays().remove(p);
        }
        currentPolylines.clear();

        for (PolylineResult pr : results) {
            Polyline pl = new Polyline();
            pl.setPoints(pr.getPoints());

            int score = pr.getScore();
            if (score >= 20) pl.setColor(0xDD228B22);
            else if (score >= 10) pl.setColor(0xDDFFA500);
            else pl.setColor(0xCCDC143C);

            pl.setWidth(12.0f);

            if (!isDrawingRoute) {
                pl.setOnClickListener((polyline, mapView, eventPos) -> {
                    // CHANGED: Pass scoreCalculator to dialog
                    EnhancedPolylineDetailsDialog.show(MainActivity.this, pr, scoreCalculator);
                    return true;
                });
            } else {
                pl.setOnClickListener(null);
            }

            map.getOverlays().add(pl);
            currentPolylines.add(pl);
        }
        map.invalidate();
    }

    private void initializeWeights() {
        weights.put("surface", 10);
        weights.put("smoothness", 5);
        weights.put("tracktype", 10);
        weights.put("bicycle", 0);
        weights.put("width", 10);
        weights.put("length", 10);
        weights.put("slope", 10);

        loadWeights();
    }

    private void loadWeights() {
        for (String key : weights.keySet()) {
            int saved = prefs.getInt("weight_" + key, weights.get(key));
            weights.put(key, saved);
        }
        if (scoreCalculator != null) {
            scoreCalculator.updateWeights(weights);
        }
    }

    private boolean bboxSimilar(BoundingBox a, BoundingBox b) {
        double tol = 0.002;
        return Math.abs(a.getLatNorth() - b.getLatNorth()) < tol &&
                Math.abs(a.getLatSouth() - b.getLatSouth()) < tol &&
                Math.abs(a.getLonEast() - b.getLonEast()) < tol &&
                Math.abs(a.getLonWest() - b.getLonWest()) < tol;
    }

    private static final int REQ_PERM_LOCATION = 1234;

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERM_LOCATION) {
            // handle if needed
        }
    }

    private void showBikeTypeDialog() {
        BikeTypeSelectionDialog.show(this, bikeTypeManager.getCurrentBikeType(),
                bikeTypeManager.isElevationDataEnabled(),
                new BikeTypeSelectionDialog.BikeTypeSelectionCallback() {
                    @Override
                    public void onBikeTypeSelected(BikeType bikeType) {
                        bikeTypeManager.setBikeType(bikeType);
                        updateUIForBikeType();
                        Toast.makeText(MainActivity.this,
                                "Selected: " + bikeType.getDisplayName(), Toast.LENGTH_SHORT).show();
                        invalidateCache();
                    }

                    @Override
                    public void onCustomCriteriaRequested() {
                        showCustomCriteriaDialog();
                    }

                    @Override
                    public void onElevationSettingChanged(boolean enabled) {
                        bikeTypeManager.setElevationDataEnabled(enabled);
                        invalidateCache();

                        String message = enabled ?
                                "Elevation analysis enabled" : "Elevation analysis disabled";
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showCustomCriteriaDialog() {
        CustomCriteriaDialog.show(this, bikeTypeManager.getCustomWeights(),
                new CustomCriteriaDialog.CustomCriteriaCallback() {
                    @Override
                    public void onCriteriaSaved(Map<String, Integer> weights) {
                        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
                            bikeTypeManager.updateCustomWeight(entry.getKey(), entry.getValue());
                        }
                        bikeTypeManager.saveCustomWeights();

                        Toast.makeText(MainActivity.this,
                                "Custom criteria saved", Toast.LENGTH_SHORT).show();
                        invalidateCache();
                    }
                });
    }

    private void updateUIForBikeType() {
        BikeType currentType = bikeTypeManager.getCurrentBikeType();

        bikeTypeButton.setText(currentType.getEmoji());

        switch (currentType) {
            case RACE_ROAD:
                findButton.setText("Find Roads");
                break;
            case GRAVEL_BIKE:
                findButton.setText("Find Gravel");
                break;
            case RACE_BIKEPACKING:
                findButton.setText("Find Routes");
                break;
            case GRAVEL_BIKEPACKING:
                findButton.setText("Find Touring");
                break;
            case CUSTOM:
                findButton.setText("Find Custom");
                break;
        }

        if (currentType == BikeType.CUSTOM) {
            criteriaButton.setText("🎛️");
        } else {
            criteriaButton.setText("⚙️");
        }

        if (legendView != null) {
            LegendView.updateForBikeType(legendView, currentType);
        }
    }

    private void invalidateCache() {
        lastBBoxQueried = null;
        lastResultsCache = null;
        lastQueryTimeMs = 0;
        hasLoadedRoads = false;
    }

    private void openTutorial() {
        Intent intent = new Intent(this, TutorialActivity.class);
        startActivity(intent);
    }

    private void fetchWeatherForCurrentViewport() {
        BoundingBox bbox = map.getBoundingBox();

        if (weatherLegendView != null) {
            WeatherLegendView.setLoading(weatherLegendView, true);
        }

        Log.d(TAG, "Fetching weather for viewport");

        // Calculate center point
        double centerLat = (bbox.getLatNorth() + bbox.getLatSouth()) / 2.0;
        double centerLon = (bbox.getLonEast() + bbox.getLonWest()) / 2.0;
        GeoPoint centerPoint = new GeoPoint(centerLat, centerLon);

        WeatherService.fetchWeatherForViewport(bbox,
                new WeatherService.WeatherCallback() {
                    @Override
                    public void onWeatherDataReceived(WeatherService.WeatherCondition condition) {
                        // Track where we fetched weather
                        lastWeatherFetchLocation = centerPoint;

                        // Set weather condition in calculator
                        scoreCalculator.setWeatherCondition(condition);

                        // Update legend with animation
                        if (weatherLegendView != null) {
                            WeatherLegendView.updateWeatherStatus(weatherLegendView, condition);
                            WeatherLegendView.animateUpdate(weatherLegendView);
                        }

                        // Show notification if muddy
                        if (condition.isMuddy) {
                            Toast.makeText(MainActivity.this,
                                    "⚠️ " + condition.rainyDaysCount + " rainy days detected - dirt roads may be muddy",
                                    Toast.LENGTH_LONG).show();
                        }

                        // Re-score existing roads if we have them
                        if (lastResultsCache != null && !lastResultsCache.isEmpty()) {
                            rescoreRoadsWithWeather();
                        }

                        Log.d(TAG, "Weather updated for region: " + condition.rainyDaysCount +
                                " rainy days, muddy=" + condition.isMuddy);
                    }

                    @Override
                    public void onError(String error) {
                        Log.w(TAG, "Weather fetch failed: " + error);
                        if (weatherLegendView != null) {
                            WeatherLegendView.updateWeatherStatus(weatherLegendView, null);
                        }
                    }
                });
    }

    /**
     * Re-score all cached roads with current weather conditions
     */
    private void rescoreRoadsWithWeather() {
        if (lastResultsCache == null || lastResultsCache.isEmpty()) {
            return;
        }

        Log.d(TAG, "Re-scoring " + lastResultsCache.size() + " roads with weather data");

        for (PolylineResult road : lastResultsCache) {
            // Recalculate score with weather-aware calculator
            int newScore;
            double maxSlope = road.getMaxSlopePercent();

            if (maxSlope >= 0) {
                newScore = scoreCalculator.calculateScoreWithSlope(
                        road.getTags(),
                        road.getPoints(),
                        maxSlope
                );
            } else {
                newScore = scoreCalculator.calculateScore(
                        road.getTags(),
                        road.getPoints()
                );
            }

            road.setScore(newScore);
        }

        // Update the map display
        updateMapFilter();
    }

    /**
     * Enhanced handleFindGravel with weather fetching
     */
    private void handleFindGravel() {
        BoundingBox bbox = map.getBoundingBox();
        double latSpan = bbox.getLatNorth() - bbox.getLatSouth();
        double lonSpan = bbox.getLonEast() - bbox.getLonWest();

        if (latSpan > MAX_VIEWPORT_SPAN_DEG || lonSpan > MAX_VIEWPORT_SPAN_DEG) {
            Toast.makeText(this, "Viewport too large - please zoom in.", Toast.LENGTH_LONG).show();
            return;
        }

        // Check cache for road data
        if (lastBBoxQueried != null && bboxSimilar(bbox, lastBBoxQueried)
                && lastResultsCache != null
                && (System.currentTimeMillis() - lastQueryTimeMs) < CACHE_TTL_MS) {

            // Roads are cached, but check if we need fresh weather data
            GeoPoint currentCenter = new GeoPoint(
                    (bbox.getLatNorth() + bbox.getLatSouth()) / 2.0,
                    (bbox.getLonEast() + bbox.getLonWest()) / 2.0
            );

            if (shouldRefetchWeather(currentCenter)) {
                Log.d(TAG, "Location changed significantly - fetching fresh weather data");
                fetchWeatherAndRescoreRoads(currentCenter);
            }

            updateMapFilter();
            return;
        }

        // STEP 1: Fetch weather for this region first
        fetchWeatherForCurrentViewport();

        // STEP 2: Then fetch road data
        OverpassService.fetchData(bbox, scoreCalculator, bikeTypeManager, new OverpassService.OverpassCallback() {
            @Override
            public void onPreExecute() {
                findButton.setEnabled(false);
                progressBar.setVisibility(View.VISIBLE);

                String loadingMessage = getLoadingMessage();
                Toast.makeText(MainActivity.this, loadingMessage, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSuccess(List<PolylineResult> results) {
                findButton.setEnabled(true);
                progressBar.setVisibility(View.GONE);
                lastBBoxQueried = bbox;
                lastResultsCache = results;
                lastQueryTimeMs = System.currentTimeMillis();
                hasLoadedRoads = true;
                routeManager.setLastResults(results);
                updateMapFilter();

                String resultMessage = getResultMessage(results.size());
                Toast.makeText(MainActivity.this, resultMessage, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                findButton.setEnabled(true);
                progressBar.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean shouldRefetchWeather(GeoPoint currentLocation) {
        if (lastWeatherFetchLocation == null) {
            return true; // Never fetched before
        }

        double distanceKm = lastWeatherFetchLocation.distanceToAsDouble(currentLocation) / 1000.0;
        Log.d(TAG, String.format("Distance from last weather fetch: %.2f km", distanceKm));

        return distanceKm >= WEATHER_REFETCH_DISTANCE_KM;
    }

    private void fetchWeatherAndRescoreRoads(GeoPoint location) {
        if (weatherLegendView != null) {
            WeatherLegendView.setLoading(weatherLegendView, true);
        }

        WeatherService.fetchWeatherCondition(location, new WeatherService.WeatherCallback() {
            @Override
            public void onWeatherDataReceived(WeatherService.WeatherCondition condition) {
                // Update last fetch location
                lastWeatherFetchLocation = location;

                // Set weather condition in calculator
                scoreCalculator.setWeatherCondition(condition);

                // Update legend with animation
                if (weatherLegendView != null) {
                    WeatherLegendView.updateWeatherStatus(weatherLegendView, condition);
                    WeatherLegendView.animateUpdate(weatherLegendView);
                }

                // Show notification if conditions changed
                if (condition.isMuddy) {
                    Toast.makeText(MainActivity.this,
                            "Weather updated: " + condition.rainyDaysCount + " rainy days - dirt roads may be muddy",
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this,
                            "Weather updated: Good conditions",
                            Toast.LENGTH_SHORT).show();
                }

                // Re-score existing roads with new weather
                if (lastResultsCache != null && !lastResultsCache.isEmpty()) {
                    Log.d(TAG, "Re-scoring roads with fresh weather data");
                    rescoreRoadsWithWeather();
                }

                Log.d(TAG, "Weather updated for new location: " + condition.rainyDaysCount +
                        " rainy days, muddy=" + condition.isMuddy);
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "Weather fetch failed: " + error);
                if (weatherLegendView != null) {
                    WeatherLegendView.updateWeatherStatus(weatherLegendView, null);
                }
            }
        });
    }

    /**
     * Enhanced showWeatherDialog with better error handling
     */
    private void showWeatherDialog() {
        WeatherService.WeatherCondition condition = scoreCalculator.getCurrentWeatherCondition();

        if (condition != null) {
            WeatherWarningDialog.show(this, condition);
        } else {
            // Try fetching fresh data for current viewport
            Toast.makeText(this, "Fetching weather data...", Toast.LENGTH_SHORT).show();
            fetchWeatherForCurrentViewport();

            // Show dialog after a delay to allow weather to load
            new Handler().postDelayed(() -> {
                WeatherService.WeatherCondition cond = scoreCalculator.getCurrentWeatherCondition();
                if (cond != null) {
                    WeatherWarningDialog.show(this, cond);
                } else {
                    WeatherWarningDialog.showError(this, "Weather data could not be retrieved");
                }
            }, 2000);
        }
    }
}