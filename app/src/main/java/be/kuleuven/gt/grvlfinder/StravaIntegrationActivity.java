package be.kuleuven.gt.grvlfinder;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;
import java.io.ByteArrayInputStream;

public class StravaIntegrationActivity extends AppCompatActivity {
    private static final String TAG = "StravaIntegration";

    private StravaApiManager stravaManager;
    private Button loginButton, logoutButton, refreshButton, backButton;
    private TextView statusText;
    private ListView routesListView;
    private ProgressBar progressBar;

    private ArrayAdapter<StravaApiManager.StravaRoute> routesAdapter;
    private List<StravaApiManager.StravaRoute> stravaRoutes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_strava_integration);

        stravaManager = new StravaApiManager(this);

        initializeUI();
        setupEventHandlers();
        updateUI();

        // Check if this activity was launched from a deep link (OAuth callback)
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void initializeUI() {
        loginButton = findViewById(R.id.loginButton);
        logoutButton = findViewById(R.id.logoutButton);
        refreshButton = findViewById(R.id.refreshButton);
        backButton = findViewById(R.id.backButton);
        statusText = findViewById(R.id.statusText);
        routesListView = findViewById(R.id.routesListView);
        progressBar = findViewById(R.id.progressBar);

        // Initialize list adapter with better layout
        routesAdapter = new ArrayAdapter<StravaApiManager.StravaRoute>(this, R.layout.list_item_route) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(R.layout.list_item_route, parent, false);
                }

                StravaApiManager.StravaRoute route = getItem(position);

                TextView nameText = convertView.findViewById(R.id.routeName);
                TextView detailsText = convertView.findViewById(R.id.routeDetails);

                nameText.setText(route.name);
                detailsText.setText(String.format("%.1f km • %s", route.distance / 1000,
                        route.isPrivate ? "Private" : "Public"));

                return convertView;
            }
        };
        routesListView.setAdapter(routesAdapter);


        progressBar.setVisibility(View.GONE);
    }

    private void setupEventHandlers() {
        loginButton.setOnClickListener(v -> initiateStravaLogin());
        logoutButton.setOnClickListener(v -> logout());
        refreshButton.setOnClickListener(v -> loadRoutes());
        backButton.setOnClickListener(v -> finish());

        routesListView.setOnItemClickListener((parent, view, position, id) -> {
            if (stravaRoutes != null && position < stravaRoutes.size()) {
                StravaApiManager.StravaRoute selectedRoute = stravaRoutes.get(position);
                downloadAndAnalyzeRoute(selectedRoute);
            }
        });
    }

    private void handleIntent(Intent intent) {
        Uri data = intent.getData();

        if (data != null) {
            Log.d(TAG, "Received intent with URI: " + data.toString());

            // Check if this is the OAuth callback from Strava
            if ("http".equals(data.getScheme()) &&
                    "localhost".equals(data.getHost()) &&
                    "/exchange_token".equals(data.getPath())) {

                Log.d(TAG, "Processing Strava OAuth callback");
                showProgress("Processing Strava authorization...");

                stravaManager.handleAuthCallback(data, new StravaApiManager.StravaCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean result) {
                        runOnUiThread(() -> {
                            hideProgress();
                            Toast.makeText(StravaIntegrationActivity.this,
                                    "Successfully connected to Strava!", Toast.LENGTH_SHORT).show();
                            updateUI();
                            loadRoutes();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            hideProgress();
                            Toast.makeText(StravaIntegrationActivity.this,
                                    "Strava authorization failed: " + error, Toast.LENGTH_LONG).show();
                            Log.e(TAG, "OAuth error: " + error);
                            updateUI();
                        });
                    }
                });
            }
        }
    }

    private void initiateStravaLogin() {
        try {
            Intent authIntent = stravaManager.createAuthIntent();
            startActivity(authIntent);
            Toast.makeText(this, "Redirecting to Strava for authorization...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error initiating Strava login", e);
            NetworkErrorHandler.showNetworkError(this, e);
        }
    }

    private void logout() {
        stravaManager.logout();
        routesAdapter.clear();
        stravaRoutes = null;
        updateUI();
        Toast.makeText(this, "Logged out from Strava", Toast.LENGTH_SHORT).show();
    }

    private void loadRoutes() {
        if (!stravaManager.isAuthenticated()) {
            Toast.makeText(this, "Please login to Strava first", Toast.LENGTH_SHORT).show();
            return;
        }

        showProgress("Loading your Strava routes...");

        stravaManager.fetchUserRoutes(new StravaApiManager.StravaCallback<List<StravaApiManager.StravaRoute>>() {
            @Override
            public void onSuccess(List<StravaApiManager.StravaRoute> routes) {
                runOnUiThread(() -> {
                    hideProgress();
                    stravaRoutes = routes;
                    routesAdapter.clear();
                    routesAdapter.addAll(routes);
                    routesAdapter.notifyDataSetChanged();

                    if (routes.isEmpty()) {
                        statusText.setText("No routes found in your Strava account.\n\n" +
                                "Note: Only routes you've created can be accessed via the API.\n" +
                                "To create routes, use Strava's Route Builder on their website.");
                        statusText.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                    } else {
                        Toast.makeText(StravaIntegrationActivity.this,
                                "Loaded " + routes.size() + " routes from Strava", Toast.LENGTH_SHORT).show();
                        updateUI(); // Reset status text
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    hideProgress();

                    Log.e(TAG, "Failed to load routes: " + error);

                    // Handle specific error types
                    if (error.contains("401") || error.contains("unauthorized")) {
                        stravaManager.logout();
                        updateUI();
                        Toast.makeText(StravaIntegrationActivity.this,
                                "Authentication expired - please login again", Toast.LENGTH_LONG).show();
                    } else if (error.contains("403") || error.contains("forbidden")) {
                        Toast.makeText(StravaIntegrationActivity.this,
                                "Access denied - check your Strava privacy settings", Toast.LENGTH_LONG).show();
                    } else if (error.contains("429")) {
                        Toast.makeText(StravaIntegrationActivity.this,
                                "Rate limit exceeded - please wait and try again later", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(StravaIntegrationActivity.this,
                                "Failed to load routes: " + error, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void downloadAndAnalyzeRoute(StravaApiManager.StravaRoute route) {
        showProgress("Downloading GPX data for: " + route.name);

        stravaManager.downloadRouteGpx(route.id, new StravaApiManager.StravaCallback<String>() {
            @Override
            public void onSuccess(String gpxData) {
                runOnUiThread(() -> {
                    hideProgress();

                    try {
                        Log.d(TAG, "Received GPX data length: " + gpxData.length());
                        Log.d(TAG, "GPX data preview: " + gpxData.substring(0, Math.min(200, gpxData.length())));

                        // Validate GPX content
                        if (!isValidGpxContent(gpxData)) {
                            Toast.makeText(StravaIntegrationActivity.this,
                                    "Invalid GPX data received", Toast.LENGTH_LONG).show();
                            return;
                        }

                        // Parse the GPX data
                        ByteArrayInputStream inputStream = new ByteArrayInputStream(gpxData.getBytes("UTF-8"));
                        GpxParser.GpxRoute gpxRoute = GpxParser.parseGpxFile(inputStream);
                        inputStream.close();

                        if (gpxRoute == null || gpxRoute.getPoints().isEmpty()) {
                            Toast.makeText(StravaIntegrationActivity.this,
                                    "No route data found in GPX - route may be empty", Toast.LENGTH_LONG).show();
                            return;
                        }

                        Log.d(TAG, "Successfully parsed GPX with " + gpxRoute.getPoints().size() + " points");

                        // Set route name if not present
                        if (gpxRoute.getName() == null || gpxRoute.getName().trim().isEmpty()) {
                            gpxRoute.setName(route.name);
                        }

                        // Launch GPX analyzer with the downloaded route
                        startGpxAnalyzerWithRoute(gpxRoute, route.name);

                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing downloaded GPX", e);
                        Toast.makeText(StravaIntegrationActivity.this,
                                "Error parsing GPX: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    hideProgress();
                    Log.e(TAG, "Failed to download route GPX: " + error);

                    // Show specific error messages
                    if (error.contains("404")) {
                        showRouteNotFoundDialog(route);
                    } else if (error.contains("403")) {
                        showPrivateRouteDialog(route);
                    } else {
                        NetworkErrorHandler.showNetworkError(StravaIntegrationActivity.this, new Exception(error));
                        showRouteDownloadFailedDialog(route);
                    }
                });
            }
        });
    }

    private boolean isValidGpxContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }

        String lowerContent = content.toLowerCase().trim();

        // Must contain GPX XML structure
        return (lowerContent.startsWith("<?xml") || lowerContent.contains("<gpx")) &&
                lowerContent.contains("gpx") &&
                (lowerContent.contains("<trk") || lowerContent.contains("<rte") || lowerContent.contains("<wpt"));
    }

    private void startGpxAnalyzerWithRoute(GpxParser.GpxRoute route, String routeName) {
        // Store the route data temporarily so GpxAnalyzerActivity can access it
        TemporaryDataHolder.getInstance().setRoute(route, routeName);

        Intent intent = new Intent(this, GpxAnalyzerActivity.class);
        intent.putExtra("from_strava", true);
        intent.putExtra("route_name", routeName);
        startActivity(intent);

        Toast.makeText(this, "Opening route analysis...", Toast.LENGTH_SHORT).show();
    }

    private void showRouteNotFoundDialog(StravaApiManager.StravaRoute route) {
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(this);

        builder.setTitle("Route Not Found");
        builder.setMessage("The route '" + route.name + "' could not be found.\n\n" +
                "This might happen if:\n" +
                "• The route was deleted\n" +
                "• The route became private\n" +
                "• There was a temporary Strava API issue");

        builder.setPositiveButton("OK", null);
        builder.show();
    }

    private void showPrivateRouteDialog(StravaApiManager.StravaRoute route) {
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(this);

        builder.setTitle("Private Route");
        builder.setMessage("Access denied for route: " + route.name + "\n\n" +
                "This route may be private or you may not have permission to access it.\n\n" +
                "Try making the route public in your Strava settings if you own it.");

        builder.setPositiveButton("Open in Strava", (dialog, which) -> {
            openRouteInStrava(route);
        });

        builder.setNegativeButton("OK", null);
        builder.show();
    }

    private void showRouteDownloadFailedDialog(StravaApiManager.StravaRoute route) {
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(this);

        builder.setTitle("Route Download Failed");
        builder.setMessage("Unable to download GPX data for: " + route.name + "\n\n" +
                "This might be because:\n" +
                "• The route is private\n" +
                "• You don't have permission to export it\n" +
                "• Strava API limitations\n" +
                "• Network connectivity issues\n\n" +
                "Try opening the route in Strava and manually exporting it as GPX.");

        builder.setPositiveButton("Open in Strava", (dialog, which) -> {
            openRouteInStrava(route);
        });

        builder.setNeutralButton("Manual Export Guide", (dialog, which) -> {
            showStravaExportGuide();
        });

        builder.setNegativeButton("OK", null);
        builder.show();
    }

    private void openRouteInStrava(StravaApiManager.StravaRoute route) {
        try {
            String stravaUrl = "https://www.strava.com/routes/" + route.id;
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(stravaUrl));
            startActivity(browserIntent);
            Toast.makeText(this, "Opening route in browser - export as GPX and share back", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open browser", Toast.LENGTH_SHORT).show();
        }
    }

    private void showStravaExportGuide() {
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(this);

        builder.setTitle("How to Export GPX from Strava");
        builder.setMessage("To manually export a GPX file from Strava:\n\n" +
                "Mobile App:\n" +
                "• Open the route in Strava app\n" +
                "• Tap the menu (⋯) in the top right\n" +
                "• Select 'Export GPX'\n" +
                "• Share the exported file with this app\n\n" +
                "Website:\n" +
                "• Visit the route on strava.com\n" +
                "• Look for 'Export GPX' button\n" +
                "• Download the file\n" +
                "• Share it with this app or use 'Select File'\n\n" +
                "Note: You can only export routes you created yourself.");

        builder.setPositiveButton("Got it", null);
        builder.show();
    }

    private void updateUI() {
        boolean isAuthenticated = stravaManager.isAuthenticated();

        loginButton.setVisibility(isAuthenticated ? View.GONE : View.VISIBLE);
        logoutButton.setVisibility(isAuthenticated ? View.VISIBLE : View.GONE);
        refreshButton.setVisibility(isAuthenticated ? View.VISIBLE : View.GONE);
        routesListView.setVisibility(isAuthenticated ? View.VISIBLE : View.GONE);

        if (isAuthenticated) {
            statusText.setText("Connected to Strava\nTap 'Refresh' to load your routes\n\nNote: Only routes you created can be accessed");
            statusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            statusText.setText("Not connected to Strava\nTap 'Login' to connect your account");
            statusText.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
        }
    }

    private void showProgress(String message) {
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText(message);
        statusText.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        loginButton.setEnabled(false);
        refreshButton.setEnabled(false);
        routesListView.setEnabled(false);
    }

    private void hideProgress() {
        progressBar.setVisibility(View.GONE);
        loginButton.setEnabled(true);
        refreshButton.setEnabled(true);
        routesListView.setEnabled(true);
        updateUI();
    }
}