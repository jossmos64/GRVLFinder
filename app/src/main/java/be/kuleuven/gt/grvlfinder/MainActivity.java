package be.kuleuven.gt.grvlfinder;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
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

    private static final double MAX_VIEWPORT_SPAN_DEG = 0.3;
    private static final long CACHE_TTL_MS = 60 * 1000;

    private Button findButton, exportButton, undoButton, criteriaButton, drawExploreButton, gpxAnalyzerButton;
    private ProgressBar progressBar;
    private Switch satelliteSwitch;

    private Map<String, Integer> weights = new HashMap<>();
    private ScoreCalculator scoreCalculator;
    private FilterManager filterManager;
    private RouteManager routeManager;

    private List<Polyline> currentPolylines = new ArrayList<>();
    private BoundingBox lastBBoxQueried = null;
    private List<PolylineResult> lastResultsCache = null;
    private long lastQueryTimeMs = 0;

    private boolean isDrawingRoute = false;
    private boolean hasLoadedRoads = false; // NEW: Track if roads have been loaded
    private Button bikeTypeButton;
    private BikeTypeManager bikeTypeManager;
    private View legendView;
    private Button tutorialButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeMap(findViewById(R.id.map));

        initializeWeights();
        scoreCalculator = new ScoreCalculator(weights);
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

        LinearLayout legendContainer = findViewById(R.id.legendContainer);
        if (legendContainer != null) {
            legendView = LegendView.create(this);
            legendContainer.addView(legendView);
            LegendView.updateForBikeType(legendView, bikeTypeManager.getCurrentBikeType());
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

    private void openGpxAnalyzer() {
        Intent intent = new Intent(this, GpxAnalyzerActivity.class);
        startActivity(intent);
    }

    private void toggleDrawExploreMode() {
        // UPDATED: Check if roads have been loaded before enabling draw mode
        if (!hasLoadedRoads && !isDrawingRoute) {
            Toast.makeText(this, "Please use 'Find Gravel' first to load roads in this area", Toast.LENGTH_LONG).show();
            return;
        }

        LinearLayout bottomContainer = findViewById(R.id.bottomButtonContainer);

        if (!isDrawingRoute) {
            // Activeren van tekenmodus
            isDrawingRoute = true;
            drawExploreButton.setText("🔍");
            bottomContainer.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Route drawing activated - tap anywhere to draw", Toast.LENGTH_SHORT).show();

            if (lastResultsCache != null) {
                drawResults(filterManager.applyFilter(lastResultsCache));
            }
        } else {
            // Wisselen naar Explore modus
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

    private void handleFindGravel() {
        BoundingBox bbox = map.getBoundingBox();
        double latSpan = bbox.getLatNorth() - bbox.getLatSouth();
        double lonSpan = bbox.getLonEast() - bbox.getLonWest();

        if (latSpan > MAX_VIEWPORT_SPAN_DEG || lonSpan > MAX_VIEWPORT_SPAN_DEG) {
            Toast.makeText(this, "Viewport too large - please zoom in.", Toast.LENGTH_LONG).show();
            return;
        }

        if (lastBBoxQueried != null && bboxSimilar(bbox, lastBBoxQueried)
                && lastResultsCache != null
                && (System.currentTimeMillis() - lastQueryTimeMs) < CACHE_TTL_MS) {
            updateMapFilter();
            return;
        }

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
                hasLoadedRoads = true; // UPDATED: Mark roads as loaded
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

            // Show progress
            progressBar.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Adding elevation data to route...", Toast.LENGTH_SHORT).show();

            // UPDATED: Use helper to ensure elevation data is added
            GpxElevationHelper.addElevationToRoute(this, routeManager.getDrawnRoute(),
                    new GpxElevationHelper.ElevationCallback() {
                        @Override
                        public void onElevationAdded(List<GeoPoint> routeWithElevation) {
                            progressBar.setVisibility(View.GONE);

                            // Export with elevation data
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

                            // Export without elevation as fallback
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
            if (score >= 20) pl.setColor(0xDD228B22); // green
            else if (score >= 10) pl.setColor(0xDDFFA500); // orange
            else pl.setColor(0xCCDC143C); // red

            pl.setWidth(12.0f);

            // Only enable clicking when NOT in drawing mode
            if (!isDrawingRoute) {
                pl.setOnClickListener((polyline, mapView, eventPos) -> {
                    PolylineDetailsDialog.show(MainActivity.this, pr);
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
        hasLoadedRoads = false; // UPDATED: Reset roads loaded flag
    }

    private void openTutorial() {
        Intent intent = new Intent(this, TutorialActivity.class);
        startActivity(intent);
    }
}