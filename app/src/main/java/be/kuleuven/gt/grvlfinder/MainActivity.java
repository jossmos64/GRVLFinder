package be.kuleuven.gt.grvlfinder;

import android.app.AlertDialog;
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

    private Button findButton, exportButton, undoButton, criteriaButton, drawExploreButton;
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

    private boolean isDrawingRoute = false; // nieuwe state

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Map initialiseren via BaseMapActivity
        initializeMap(findViewById(R.id.map));

        initializeWeights();
        scoreCalculator = new ScoreCalculator(weights);
        filterManager = new FilterManager();
        routeManager = new RouteManager(map);

        bindUI();
        setupEventHandlers();

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

        LinearLayout legendContainer = findViewById(R.id.legendContainer);
        if (legendContainer != null) {
            legendContainer.addView(LegendView.create(this));
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

        if (criteriaButton != null) {
            criteriaButton.setOnClickListener(v ->
                    CriteriaSettingsDialog.show(this, weights));
        }

        drawExploreButton.setOnClickListener(v -> toggleDrawExploreMode());

        // Tap events op de kaart
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

    private void toggleDrawExploreMode() {
        LinearLayout bottomContainer = findViewById(R.id.bottomButtonContainer);

        if (!isDrawingRoute) {
            // Activeren van tekenmodus
            isDrawingRoute = true;
            drawExploreButton.setText("\uD83D\uDD0D");
            bottomContainer.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Route tekenen geactiveerd", Toast.LENGTH_SHORT).show();
        } else {
            // Wisselen naar Explore modus
            isDrawingRoute = false;
            drawExploreButton.setText("✏\uFE0F");
            bottomContainer.setVisibility(View.GONE);
            routeManager.clearRoute();
            Toast.makeText(this, "Explore modus geactiveerd", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleFindGravel() {
        BoundingBox bbox = map.getBoundingBox();
        double latSpan = bbox.getLatNorth() - bbox.getLatSouth();
        double lonSpan = bbox.getLonEast() - bbox.getLonWest();

        if (latSpan > MAX_VIEWPORT_SPAN_DEG || lonSpan > MAX_VIEWPORT_SPAN_DEG) {
            Toast.makeText(this, "Viewport te groot - zoom in aub.", Toast.LENGTH_LONG).show();
            return;
        }

        if (lastBBoxQueried != null && bboxSimilar(bbox, lastBBoxQueried)
                && lastResultsCache != null
                && (System.currentTimeMillis() - lastQueryTimeMs) < CACHE_TTL_MS) {
            updateMapFilter();
            return;
        }

        OverpassService.fetchData(bbox, scoreCalculator, new OverpassService.OverpassCallback() {
            @Override
            public void onPreExecute() {
                findButton.setEnabled(false);
                progressBar.setVisibility(View.VISIBLE);
                Toast.makeText(MainActivity.this, "Zoeken naar gravelwegen...", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSuccess(List<PolylineResult> results) {
                findButton.setEnabled(true);
                progressBar.setVisibility(View.GONE);
                lastBBoxQueried = bbox;
                lastResultsCache = results;
                lastQueryTimeMs = System.currentTimeMillis();
                routeManager.setLastResults(results);
                updateMapFilter();
                Toast.makeText(MainActivity.this, "Gevonden: " + results.size() + " wegen", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                findButton.setEnabled(true);
                progressBar.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this, "Fout: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void handleExportGpx() {
        if (!routeManager.hasRoute()) {
            Toast.makeText(this, "Geen route getekend", Toast.LENGTH_SHORT).show();
            return;
        }

        // Inflate custom dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_route_name, null);
        EditText input = dialogView.findViewById(R.id.routeNameInput);
        Button cancelBtn = dialogView.findViewById(R.id.cancelButton);
        Button saveBtn = dialogView.findViewById(R.id.saveButton);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        // Cancel button
        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        // Save button
        saveBtn.setOnClickListener(v -> {
            String routeName = input.getText().toString().trim();
            if (routeName.isEmpty()) {
                routeName = "route";
            }
            String filename = routeName.replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".gpx";

            GpxExporter.exportRoute(this, routeManager.getDrawnRoute(), filename, new GpxExporter.ExportCallback() {
                @Override
                public void onSuccess(String message) {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
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
            if (score >= 20) pl.setColor(0xDD228B22); // groen
            else if (score >= 10) pl.setColor(0xDDFFA500); // oranje
            else pl.setColor(0xCCDC143C); // rood

            pl.setWidth(6.0f);

            pl.setOnClickListener((polyline, mapView, eventPos) -> {
                PolylineDetailsDialog.show(MainActivity.this, pr);
                return true;
            });

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

    private void checkAndRequestLocationPermission() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    REQ_PERM_LOCATION);
        } else {
            // enabled location features if any
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERM_LOCATION) {
            // handle if needed
        }
    }

    private void checkRoutingProviderWarning() {
        android.content.SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
        String base = prefs.getString("routing_base_url", OSRMRoutingService.DEFAULT_OSRM_URL);
        boolean ignore = prefs.getBoolean("ignore_osrm_demo_warning", false);
        if (base.contains("router.project-osrm.org") && !ignore) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Routing provider waarschuwing")
                    .setMessage("Je gebruikt de publieke OSRM-demo server (router.project-osrm.org). Deze server is **niet** bedoeld voor productie/distributie — hij kan worden beperkt of uitgeschakeld. Voor publicatie: host je eigen OSRM of gebruik een commerciële routing API en zet hier die base URL. Wil je doorgaan?")
                    .setPositiveButton("Doorgaan", (d, w) -> d.dismiss())
                    .setNeutralButton("Niet meer tonen", (d, w) -> {
                        prefs.edit().putBoolean("ignore_osrm_demo_warning", true).apply();
                        d.dismiss();
                    })
                    .setNegativeButton("Instellingen", (d, w) -> {
                        startActivity(new android.content.Intent(this, SettingsActivity.class));
                        d.dismiss();
                    })
                    .show();
        }
    }

}
