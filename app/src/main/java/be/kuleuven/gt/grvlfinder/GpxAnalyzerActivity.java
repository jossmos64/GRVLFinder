package be.kuleuven.gt.grvlfinder;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;

public class GpxAnalyzerActivity extends AppCompatActivity {
    private static final String TAG = "GpxAnalyzerActivity";
    private static final int PICK_GPX_FILE = 1001;

    private Button selectFileButton;
    private Button analyzeButton;
    private Button backButton;
    private TextView fileInfoText;
    private ProgressBar progressBar;
    private TextView progressText;
    private Spinner bikeTypeSpinner;

    private BikeTypeManager bikeTypeManager;
    private GpxParser.GpxRoute loadedRoute;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gpx_analyzer);

        // Initialize BikeTypeManager with preferences
        SharedPreferences prefs = getSharedPreferences("bike_prefs", MODE_PRIVATE);
        bikeTypeManager = new BikeTypeManager(prefs);

        // Initialize UI components
        initializeUI();
        setupEventHandlers();
        setupBikeTypeSpinner();

        // Check if this is from Strava integration
        if (getIntent().getBooleanExtra("from_strava", false)) {
            handleStravaRoute();
        } else {
            // Handle incoming share Intent or file opening
            handleIncomingIntent(getIntent());
        }
    }

    private void handleStravaRoute() {
        if (TemporaryDataHolder.getInstance().hasRoute()) {
            loadedRoute = TemporaryDataHolder.getInstance().getRoute();
            String routeName = TemporaryDataHolder.getInstance().getRouteName();

            // Clear the temporary data
            TemporaryDataHolder.getInstance().clear();

            if (loadedRoute != null && !loadedRoute.getPoints().isEmpty()) {
                // Update UI with route info
                updateFileInfoForStravaRoute(routeName, loadedRoute);
                analyzeButton.setEnabled(true);

                Toast.makeText(this, "Strava route loaded successfully", Toast.LENGTH_SHORT).show();

                // Auto-start analysis after a short delay
                fileInfoText.postDelayed(this::analyzeRoute, 1000);
            } else {
                Toast.makeText(this, "Failed to load Strava route", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "No Strava route data available", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void updateFileInfoForStravaRoute(String routeName, GpxParser.GpxRoute route) {
        double distance = GpxParser.calculateRouteDistance(route.getPoints()) / 1000.0;
        boolean hasElevation = GpxParser.hasElevationData(route.getPoints());

        StringBuilder info = new StringBuilder();
        info.append("Strava Route\n");
        if (routeName != null && !routeName.trim().isEmpty()) {
            info.append("Route: ").append(routeName).append("\n");
        }
        info.append(String.format("Points: %d\n", route.getPoints().size()));
        info.append(String.format("Distance: %.2f km\n", distance));
        info.append("Elevation data: ").append(hasElevation ? "Available" : "Not available").append("\n");
        info.append("Source: Strava");

        fileInfoText.setText(info.toString());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void initializeUI() {
        selectFileButton = findViewById(R.id.selectFileButton);
        analyzeButton = findViewById(R.id.analyzeButton);
        backButton = findViewById(R.id.backButton);
        fileInfoText = findViewById(R.id.fileInfoText);
        progressBar = findViewById(R.id.progressBar);
        progressText = findViewById(R.id.progressText);
        bikeTypeSpinner = findViewById(R.id.bikeTypeSpinner);

        // Add Strava button initialization
        Button stravaButton = findViewById(R.id.stravaButton);

        analyzeButton.setEnabled(false);
        progressBar.setVisibility(ProgressBar.GONE);
        progressText.setVisibility(TextView.GONE);

        // Set up Strava button click listener
        if (stravaButton != null) {
            stravaButton.setOnClickListener(v -> openStravaIntegration());
        }
    }

    private void openStravaIntegration() {
        Intent intent = new Intent(this, StravaIntegrationActivity.class);
        startActivity(intent);
    }

    private void setupEventHandlers() {
        selectFileButton.setOnClickListener(v -> openFilePicker());
        analyzeButton.setOnClickListener(v -> analyzeRoute());
        backButton.setOnClickListener(v -> finish());
    }

    private void setupBikeTypeSpinner() {
        ArrayAdapter<BikeType> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, BikeType.values());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        bikeTypeSpinner.setAdapter(adapter);

        // Set current selection
        BikeType current = bikeTypeManager.getCurrentBikeType();
        int pos = adapter.getPosition(current);
        if (pos >= 0) bikeTypeSpinner.setSelection(pos);

        bikeTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean first = true;
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                if (first) { first = false; return; }
                BikeType selected = (BikeType) parent.getItemAtPosition(position);
                bikeTypeManager.setBikeType(selected);
                Toast.makeText(GpxAnalyzerActivity.this,
                        "Selected bike type: " + selected.getDisplayName(), Toast.LENGTH_SHORT).show();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void showExportGuide(String serviceName) {
        StringBuilder guide = new StringBuilder();
        guide.append("Detailed Export Guide for ").append(serviceName).append(":\n\n");

        if (serviceName.equals("Garmin Connect")) {
            guide.append("METHOD 1 - Mobile App:\n");
            guide.append("• Open Garmin Connect app\n");
            guide.append("• Go to Menu → Activities or Courses\n");
            guide.append("• Find and open your course\n");
            guide.append("• Tap the Share icon or menu (⋯)\n");
            guide.append("• Look for 'Export' or 'Send to Device'\n");
            guide.append("• Select 'Export as GPX'\n\n");

            guide.append("METHOD 2 - Website:\n");
            guide.append("• Visit connect.garmin.com on computer\n");
            guide.append("• Navigate to your course\n");
            guide.append("• Click the gear/settings icon\n");
            guide.append("• Select 'Export to GPX'\n\n");

            guide.append("METHOD 3 - Direct Link:\n");
            guide.append("• Make the course public in Garmin Connect\n");
            guide.append("• The GPX might be directly accessible\n");

        } else if (serviceName.equals("Strava")) {
            guide.append("IMPORTANT: Only works for routes you created!\n\n");
            guide.append("METHOD 1 - Mobile App:\n");
            guide.append("• Open Strava app\n");
            guide.append("• Go to 'You' → 'Routes'\n");
            guide.append("• Select your route\n");
            guide.append("• Tap menu (⋯) → 'Export GPX'\n\n");

            guide.append("METHOD 2 - Website:\n");
            guide.append("• Visit strava.com on computer\n");
            guide.append("• Go to Dashboard → My Routes\n");
            guide.append("• Click on your route\n");
            guide.append("• Click 'Export GPX' button\n\n");

            guide.append("NOTE: You cannot export other people's routes unless they're public and you have permission.");

        } else {
            guide.append("General steps for most fitness apps:\n");
            guide.append("• Open the route/activity in the app or website\n");
            guide.append("• Look for: Share, Export, Download, or Settings menu\n");
            guide.append("• Choose GPX, TCX, or Track format\n");
            guide.append("• Save the file and share it with this app\n");
        }

        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(this);

        builder.setTitle("How to Export GPX Files");
        builder.setMessage(guide.toString());
        builder.setPositiveButton("Got it", null);
        builder.setNegativeButton("Try File Picker", (dialog, which) -> {
            openFilePicker();
        });

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();

        // Make the text scrollable for long content
        android.widget.TextView textView = dialog.findViewById(android.R.id.message);
        if (textView != null) {
            textView.setMovementMethod(android.text.method.ScrollingMovementMethod.getInstance());
            textView.setMaxLines(20);
        }
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) {
            Log.d(TAG, "No intent received");
            return;
        }

        String action = intent.getAction();
        String type = intent.getType();

        Log.d(TAG, "Handling intent - Action: " + action + ", Type: " + type);

        // Debug: Print all intent data
        if (intent.getExtras() != null) {
            Log.d(TAG, "Intent extras: " + intent.getExtras().toString());
        }

        Uri fileUri = null;
        String sharedUrl = null;

        if (Intent.ACTION_SEND.equals(action)) {
            Log.d(TAG, "Processing ACTION_SEND intent");

            // Try to get URI first
            fileUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (fileUri == null) {
                fileUri = intent.getData();
                Log.d(TAG, "Got URI from intent.getData(): " + fileUri);
            }

            // Check for shared URLs in text
            if (fileUri == null && intent.hasExtra("android.intent.extra.TEXT")) {
                String textExtra = intent.getStringExtra(Intent.EXTRA_TEXT);
                Log.d(TAG, "Text extra found: " + textExtra);

                if (textExtra != null) {
                    // Check if it's a fitness app URL
                    if (isGpxUrl(textExtra)) {
                        sharedUrl = textExtra;
                        Log.d(TAG, "Detected GPX URL: " + sharedUrl);
                    } else if (textExtra.endsWith(".gpx") || textExtra.contains("gpx")) {
                        try {
                            fileUri = Uri.parse(textExtra);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to parse text extra as URI", e);
                        }
                    }
                }
            }

        } else if (Intent.ACTION_VIEW.equals(action)) {
            Log.d(TAG, "Processing ACTION_VIEW intent");
            fileUri = intent.getData();

            // Check if the viewed URI is a GPX URL
            if (fileUri != null && isGpxUrl(fileUri.toString())) {
                sharedUrl = fileUri.toString();
                fileUri = null; // Clear fileUri since we'll handle it as URL
            }

        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            Log.d(TAG, "Processing ACTION_SEND_MULTIPLE intent");
            if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                try {
                    java.util.ArrayList<android.os.Parcelable> parcelables = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                    if (parcelables != null && !parcelables.isEmpty()) {
                        android.os.Parcelable first = parcelables.get(0);
                        if (first instanceof Uri) {
                            fileUri = (Uri) first;
                            Log.d(TAG, "Using first URI from multiple: " + fileUri);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to process multiple URIs", e);
                }
            }
        }

        // Handle URL downloads
        if (sharedUrl != null) {
            Log.d(TAG, "Processing shared URL: " + sharedUrl);
            Toast.makeText(this, "Downloading GPX from shared link...", Toast.LENGTH_SHORT).show();
            downloadGpxFromUrl(sharedUrl);
            return;
        }

        // Handle direct file URIs
        if (fileUri != null) {
            Log.d(TAG, "Processing file URI: " + fileUri);
            Toast.makeText(this, "Processing shared GPX file...", Toast.LENGTH_SHORT).show();

            if (loadGpxFile(fileUri)) {
                if (loadedRoute != null && !loadedRoute.getPoints().isEmpty()) {
                    Toast.makeText(this, "GPX file loaded successfully - starting analysis...", Toast.LENGTH_SHORT).show();
                    fileInfoText.postDelayed(this::analyzeRoute, 500);
                } else {
                    Toast.makeText(this, "GPX file loaded but no route data found", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "Failed to load GPX file from shared source", Toast.LENGTH_LONG).show();
            }
        } else {
            Log.w(TAG, "No file URI or URL found in intent");
            if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_VIEW.equals(action)) {
                Toast.makeText(this, "No file data received. Please try sharing the GPX file again.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean isGpxUrl(String url) {
        if (url == null) return false;

        // Convert to lowercase for case-insensitive matching
        String lowerUrl = url.toLowerCase();

        // Check for known fitness app URLs that might contain GPX data, for future expansion
        return lowerUrl.contains("connect.garmin.com") ||
                lowerUrl.contains("strava.com") ||
                lowerUrl.contains("strava.app.link") || // Handle Strava app links
                lowerUrl.contains("wahoo") ||
                lowerUrl.contains("ridewithgps.com") ||
                lowerUrl.contains("komoot.com") ||
                lowerUrl.contains("mapmyride.com") ||
                lowerUrl.contains("trainingpeaks.com") ||
                lowerUrl.endsWith(".gpx") ||
                lowerUrl.contains("/gpx") ||
                lowerUrl.contains("export") && (lowerUrl.contains("gpx") || lowerUrl.contains("track")) ||
                (lowerUrl.startsWith("https://") && lowerUrl.contains("route")) || // Generic route URLs
                (lowerUrl.startsWith("https://") && lowerUrl.contains("course")); // Generic course URLs
    }

    private void downloadGpxFromUrl(String url) {
        // Show progress
        selectFileButton.setEnabled(false);
        analyzeButton.setEnabled(false);
        progressBar.setVisibility(ProgressBar.VISIBLE);
        progressText.setVisibility(TextView.VISIBLE);
        progressText.setText("Analyzing URL...");

        // Check URL type and handle accordingly
        if (url.contains("connect.garmin.com")) {
            handleGarminUrl(url);
        } else if (url.contains("strava.com")) {
            handleStravaUrl(url);
        } else if (url.contains("ridewithgps.com")) {
            handleRideWithGpsUrl(url);
        } else {
            // Try generic download
            attemptDirectDownload(url);
        }
    }

    private void handleGarminUrl(String url) {
        progressText.setText("Garmin Connect link detected...");

        new Thread(() -> {
            try {
                // Try some public Garmin URL patterns
                String[] possibleUrls = generateGarminPublicUrls(url);
                boolean success = false;

                for (String testUrl : possibleUrls) {
                    if (tryDownloadGpx(testUrl)) {
                        success = true;
                        break;
                    }
                }

                if (!success) {
                    runOnUiThread(() -> {
                        hideProgressUI();
                        showGarminExportGuide(url);
                    });
                }

            } catch (Exception e) {
                runOnUiThread(() -> {
                    hideProgressUI();
                    showGarminExportGuide(url);
                });
            }
        }).start();
    }

    private void handleStravaUrl(String url) {
        progressText.setText("Strava link detected...");

        runOnUiThread(() -> {
            hideProgressUI();

            // Show dialog suggesting Strava integration
            androidx.appcompat.app.AlertDialog.Builder builder =
                    new androidx.appcompat.app.AlertDialog.Builder(this);

            builder.setTitle("Strava Route Detected");
            builder.setMessage("This appears to be a Strava route link.\n\n" +
                    "For the best experience, use the Strava Integration feature " +
                    "which allows you to browse and analyze your Strava routes directly.\n\n" +
                    "Alternatively, you can manually export the GPX file from Strava.");

            builder.setPositiveButton("Open Strava Integration", (dialog, which) -> {
                Intent intent = new Intent(this, StravaIntegrationActivity.class);
                startActivity(intent);
            });

            builder.setNeutralButton("Manual Export Guide", (dialog, which) -> {
                showExportGuide("Strava");
            });

            builder.setNegativeButton("Try Direct Download", (dialog, which) -> {
                attemptDirectDownload(url);
            });

            builder.show();
        });
    }

    private void handleRideWithGpsUrl(String url) {
        progressText.setText("Checking Ride with GPS...");
        attemptDirectDownload(url);
    }

    private void attemptDirectDownload(String url) {
        new Thread(() -> {
            try {
                String gpxData = downloadGpxData(url);

                if (gpxData != null && !gpxData.isEmpty()) {
                    runOnUiThread(() -> {
                        try {
                            // Parse the downloaded GPX data
                            java.io.ByteArrayInputStream inputStream =
                                    new java.io.ByteArrayInputStream(gpxData.getBytes("UTF-8"));

                            loadedRoute = GpxParser.parseGpxFile(inputStream);
                            inputStream.close();

                            if (loadedRoute == null || loadedRoute.getPoints().isEmpty()) {
                                Log.w(TAG, "No route points found in downloaded GPX");
                                Toast.makeText(this, "No route points found in downloaded GPX", Toast.LENGTH_LONG).show();
                                hideProgressUI();
                                return;
                            }

                            // Calculate route info and update UI
                            updateFileInfo(url, loadedRoute);
                            analyzeButton.setEnabled(true);

                            Toast.makeText(this, "GPX downloaded successfully!", Toast.LENGTH_SHORT).show();
                            hideProgressUI();

                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing downloaded GPX data", e);
                            Toast.makeText(this, "Error parsing downloaded GPX: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                            hideProgressUI();
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        hideProgressUI();
                        showUrlDownloadOptions(url);
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Error downloading GPX from URL: " + url, e);
                runOnUiThread(() -> {
                    hideProgressUI();
                    showUrlDownloadOptions(url);
                });
            }
        }).start();
    }

    private boolean tryDownloadGpx(String url) {
        try {
            String gpxData = downloadGpxData(url);
            if (gpxData != null && isValidGpxContent(gpxData)) {
                runOnUiThread(() -> {
                    try {
                        java.io.ByteArrayInputStream inputStream =
                                new java.io.ByteArrayInputStream(gpxData.getBytes("UTF-8"));
                        loadedRoute = GpxParser.parseGpxFile(inputStream);
                        inputStream.close();

                        if (loadedRoute != null && !loadedRoute.getPoints().isEmpty()) {
                            updateFileInfo(url, loadedRoute);
                            analyzeButton.setEnabled(true);
                            Toast.makeText(this, "GPX downloaded successfully!", Toast.LENGTH_SHORT).show();
                            hideProgressUI();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing downloaded GPX", e);
                    }
                });
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to download from: " + url, e);
        }
        return false;
    }
    //doesn't work for now
    private String[] generateGarminPublicUrls(String originalUrl) {
        java.util.List<String> urls = new java.util.ArrayList<>();

        // Extract course ID from Garmin URL
        String courseId = extractCourseId(originalUrl);
        if (courseId != null) {
            // Try public GPX URLs (these work for public courses only)
            urls.add("https://connect.garmin.com/modern/proxy/course-service/course/" + courseId + "/gpx");
            urls.add("https://connect.garmin.com/course/" + courseId + "/gpx");
            urls.add("https://connect.garmin.com/modern/course/" + courseId + "/gpx");
        }

        // Add original URL as fallback
        urls.add(originalUrl);

        return urls.toArray(new String[0]);
    }

    private void showGarminExportGuide(String url) {
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(this);

        builder.setTitle("Garmin Connect Export Required");
        builder.setMessage("Most Garmin Connect routes require manual export.\n\n" +
                "To export from Garmin Connect:\n\n" +
                "Mobile App:\n" +
                "• Open the course in Garmin Connect app\n" +
                "• Tap the menu (⋯) or Share button\n" +
                "• Look for 'Export' or 'Send to Device'\n" +
                "• Select 'Export as GPX'\n" +
                "• Share the GPX file with this app\n\n" +
                "Website:\n" +
                "• Visit connect.garmin.com on computer\n" +
                "• Navigate to your course\n" +
                "• Click the gear/settings icon\n" +
                "• Select 'Export to GPX'\n" +
                "• Download and share with this app");

        builder.setPositiveButton("Open in Browser", (dialog, which) -> {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);
                Toast.makeText(this, "Export the GPX file and share it back to this app", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Cannot open browser", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Select File Instead", (dialog, which) -> {
            openFilePicker();
        });

        builder.show();
    }

    private String downloadGpxData(String url) throws Exception {
        Log.d(TAG, "Attempting to download GPX from: " + url);

        // Try different URL patterns for known services
        String[] possibleUrls = generatePossibleGpxUrls(url);

        for (String testUrl : possibleUrls) {
            try {
                Log.d(TAG, "Trying URL: " + testUrl);

                java.net.URL urlObj = new java.net.URL(testUrl);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) urlObj.openConnection();

                // Set headers to mimic a browser request
                connection.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36");
                connection.setRequestProperty("Accept",
                        "application/gpx+xml,text/xml,application/xml,text/plain,*/*");
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
                connection.setRequestProperty("Connection", "keep-alive");
                connection.setRequestProperty("Upgrade-Insecure-Requests", "1");

                // Handle redirects manually
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Response code for " + testUrl + ": " + responseCode);

                // Handle redirects
                if (responseCode >= 300 && responseCode < 400) {
                    String redirectUrl = connection.getHeaderField("Location");
                    Log.d(TAG, "Redirect to: " + redirectUrl);
                    connection.disconnect();
                    if (redirectUrl != null) {
                        // Try the redirect URL
                        testUrl = redirectUrl;
                        urlObj = new java.net.URL(testUrl);
                        connection = (java.net.HttpURLConnection) urlObj.openConnection();
                        // Set headers again for redirect
                        connection.setRequestProperty("User-Agent",
                                "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36");
                        connection.setRequestProperty("Accept",
                                "application/gpx+xml,text/xml,application/xml,text/plain,*/*");
                        connection.setConnectTimeout(15000);
                        connection.setReadTimeout(20000);
                        responseCode = connection.getResponseCode();
                        Log.d(TAG, "Redirect response code: " + responseCode);
                    }
                }

                if (responseCode == 200) {
                    java.io.InputStream inputStream = connection.getInputStream();

                    // Handle gzip encoding
                    String encoding = connection.getHeaderField("Content-Encoding");
                    if ("gzip".equalsIgnoreCase(encoding)) {
                        inputStream = new java.util.zip.GZIPInputStream(inputStream);
                    }

                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(inputStream, "UTF-8"));

                    StringBuilder response = new StringBuilder();
                    String line;
                    int lineCount = 0;
                    while ((line = reader.readLine()) != null && lineCount < 10000) { // Limit to prevent memory issues
                        response.append(line).append("\n");
                        lineCount++;
                    }
                    reader.close();

                    String content = response.toString();
                    Log.d(TAG, "Downloaded content length: " + content.length() + " characters");
                    Log.d(TAG, "First 200 characters: " + content.substring(0, Math.min(200, content.length())));

                    // Check if the response looks like GPX
                    if (isValidGpxContent(content)) {
                        Log.d(TAG, "Successfully downloaded GPX data from: " + testUrl);
                        return content;
                    } else {
                        Log.d(TAG, "Response does not contain valid GPX data");
                        // Check if it's an authentication page
                        if (content.toLowerCase().contains("sign in") ||
                                content.toLowerCase().contains("login") ||
                                content.toLowerCase().contains("authentication") ||
                                content.toLowerCase().contains("unauthorized")) {
                            Log.d(TAG, "Authentication required for: " + testUrl);
                        }
                    }
                } else if (responseCode == 401 || responseCode == 403) {
                    Log.d(TAG, "Authentication required - Response: " + responseCode);
                }

                connection.disconnect();

            } catch (Exception e) {
                Log.w(TAG, "Failed to download from: " + testUrl + " - " + e.getMessage());
                // Continue to next URL
            }
        }

        return null; // No GPX data found
    }

    private boolean isValidGpxContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }

        String lowerContent = content.toLowerCase();

        // Must contain GPX XML structure
        boolean hasGpxTag = lowerContent.contains("<gpx");
        boolean hasTrack = lowerContent.contains("<trk") || lowerContent.contains("<trkpt");
        boolean hasRoute = lowerContent.contains("<rte") || lowerContent.contains("<rtept");
        boolean hasWaypoints = lowerContent.contains("<wpt");

        // Check for XML declaration or GPX namespace
        boolean hasXmlStructure = content.trim().startsWith("<?xml") ||
                lowerContent.contains("xmlns") ||
                lowerContent.contains("gpx");

        // Must have GPX tag and some actual data
        return hasGpxTag && hasXmlStructure && (hasTrack || hasRoute || hasWaypoints);
    }

    private String[] generatePossibleGpxUrls(String originalUrl) {
        java.util.List<String> urls = new java.util.ArrayList<>();

        if (originalUrl.contains("connect.garmin.com")) {
            // Garmin Connect patterns - most require authentication
            String courseId = extractCourseId(originalUrl);
            if (courseId != null) {
                // These typically require authentication but worth trying
                urls.add("https://connect.garmin.com/modern/proxy/course-service/course/" + courseId + "/gpx");
                urls.add("https://connect.garmin.com/modern/course/" + courseId + "/gpx");
                urls.add("https://connect.garmin.com/course/" + courseId + "/gpx");
                urls.add("https://connect.garmin.com/modern/proxy/download-service/export/gpx/course/" + courseId);
            }
        } else if (originalUrl.contains("strava.com")) {
            // Strava patterns - these typically require authentication
            String routeId = extractRouteId(originalUrl);
            if (routeId != null) {
                urls.add("https://www.strava.com/routes/" + routeId + "/export_gpx");
                urls.add("https://www.strava.com/routes/" + routeId + ".gpx");
                urls.add("https://www.strava.com/api/v3/routes/" + routeId + "/export_gpx");
            }

            // Handle Strava app links
            if (originalUrl.contains("strava.app.link")) {
                // These are deep links that redirect - try the original URL first
                urls.add(originalUrl);
            }
        } else if (originalUrl.contains("ridewithgps.com")) {
            // RideWithGPS patterns
            String routeId = extractRideWithGpsId(originalUrl);
            if (routeId != null) {
                urls.add("https://ridewithgps.com/routes/" + routeId + ".gpx");
                urls.add("https://ridewithgps.com/routes/" + routeId + "/export.gpx");
            }
        } else if (originalUrl.contains("komoot.com")) {
            // Komoot patterns
            String tourId = extractKomootId(originalUrl);
            if (tourId != null) {
                urls.add("https://www.komoot.com/tour/" + tourId + ".gpx");
            }
        }

        // Add original URL
        urls.add(originalUrl);

        // Try adding common GPX export suffixes
        if (!originalUrl.endsWith(".gpx")) {
            urls.add(originalUrl + ".gpx");
            urls.add(originalUrl + "/export");
            urls.add(originalUrl + "/gpx");
            urls.add(originalUrl + "/export.gpx");
            urls.add(originalUrl + "/download.gpx");
        }

        return urls.toArray(new String[0]);
    }

    private String extractRideWithGpsId(String url) {
        try {
            String pattern = "routes/(\\d+)";
            java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher matcher = regex.matcher(url);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting RideWithGPS ID", e);
        }
        return null;
    }

    private String extractKomootId(String url) {
        try {
            String pattern = "tour/(\\d+)";
            java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher matcher = regex.matcher(url);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting Komoot ID", e);
        }
        return null;
    }

    private String extractCourseId(String url) {
        try {
            // Extract course ID from Garmin URLs like:
            // https://connect.garmin.com/modern/course/406789218
            String pattern = "course/(\\d+)";
            java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher matcher = regex.matcher(url);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting course ID", e);
        }
        return null;
    }

    private String extractRouteId(String url) {
        try {
            // Extract route ID from Strava URLs like:
            // https://www.strava.com/routes/1234567890
            String pattern = "routes/(\\d+)";
            java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher matcher = regex.matcher(url);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting route ID", e);
        }
        return null;
    }

    private void updateFileInfo(String sourceUrl, GpxParser.GpxRoute route) {
        double distance = GpxParser.calculateRouteDistance(route.getPoints()) / 1000.0;
        boolean hasElevation = GpxParser.hasElevationData(route.getPoints());
        String routeName = route.getName();

        StringBuilder info = new StringBuilder();
        info.append("Downloaded from URL\n");
        if (routeName != null && !routeName.trim().isEmpty()) {
            info.append("Route: ").append(routeName).append("\n");
        }
        info.append(String.format("Points: %d\n", route.getPoints().size()));
        info.append(String.format("Distance: %.2f km\n", distance));
        info.append("Elevation data: ").append(hasElevation ? "Available" : "Not available").append("\n");

        // Add source information
        if (sourceUrl.contains("garmin")) {
            info.append("Source: Garmin Connect");
        } else if (sourceUrl.contains("strava")) {
            info.append("Source: Strava");
        } else if (sourceUrl.contains("wahoo")) {
            info.append("Source: Wahoo");
        } else {
            info.append("Source: Downloaded from URL");
        }

        fileInfoText.setText(info.toString());
    }

    private void showUrlDownloadOptions(String url) {
        // Determine the service type for specific instructions
        String serviceName = "Unknown service";
        String specificInstructions = "";

        if (url.contains("connect.garmin.com")) {
            serviceName = "Garmin Connect";
            specificInstructions = "To export from Garmin Connect:\n\n" +
                    "1. Open the course in Garmin Connect on your phone\n" +
                    "2. Tap the menu (⋯) or Share button\n" +
                    "3. Look for 'Export' or 'Download as GPX'\n" +
                    "4. Share the downloaded GPX file with this app\n\n" +
                    "OR visit the course on a computer and use the Export option.";
        } else if (url.contains("strava.com")) {
            serviceName = "Strava";
            specificInstructions = "To export from Strava:\n\n" +
                    "1. Open the route in the Strava app\n" +
                    "2. Tap the menu (⋯) in the top right\n" +
                    "3. Select 'Export GPX'\n" +
                    "4. Share the exported file with this app\n\n" +
                    "Note: You must be the creator of the route or have proper access.";
        } else if (url.contains("ridewithgps.com")) {
            serviceName = "Ride with GPS";
            specificInstructions = "To export from Ride with GPS:\n\n" +
                    "1. Open the route in your browser\n" +
                    "2. Look for the 'Export' button\n" +
                    "3. Select 'GPX Track' format\n" +
                    "4. Download and share with this app";
        } else if (url.contains("komoot.com")) {
            serviceName = "Komoot";
            specificInstructions = "To export from Komoot:\n\n" +
                    "1. Open the tour in Komoot app or website\n" +
                    "2. Look for Export or Download options\n" +
                    "3. Select GPX format\n" +
                    "4. Share the downloaded file with this app";
        } else {
            specificInstructions = "To get a GPX file:\n\n" +
                    "1. Open the link in your browser\n" +
                    "2. Look for Export, Download, or GPX options\n" +
                    "3. Download the GPX file\n" +
                    "4. Share it with this app or use 'Select File' button";
        }

        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(this);

        builder.setTitle("GPX Download Required");
        builder.setMessage("The shared link from " + serviceName + " requires manual GPX export.\n\n" +
                "Most fitness apps require authentication and don't allow direct GPX downloads.\n\n" +
                specificInstructions);

        builder.setPositiveButton("Open Link", (dialog, which) -> {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);
                Toast.makeText(this, "Export the GPX file and share it back to this app", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Cannot open browser", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Select File Instead", (dialog, which) -> {
            openFilePicker();
        });

        builder.show();
    }
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        // Add multiple MIME types for GPX files
        String[] mimeTypes = {
                "application/gpx+xml",
                "text/xml",
                "application/xml",
                "application/octet-stream",
                "text/plain",
                "*/*"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        intent.putExtra(Intent.EXTRA_TITLE, "Select GPX File");
        startActivityForResult(intent, PICK_GPX_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_GPX_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                loadGpxFile(uri);
            }
        }
    }

    private boolean loadGpxFile(Uri uri) {
        try {
            Log.d(TAG, "Attempting to load GPX file from URI: " + uri);

            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Log.e(TAG, "Cannot open input stream for URI: " + uri);
                Toast.makeText(this, "Cannot open file - permission denied", Toast.LENGTH_SHORT).show();
                return false;
            }

            // Parse the GPX file
            loadedRoute = GpxParser.parseGpxFile(inputStream);
            inputStream.close();

            if (loadedRoute == null || loadedRoute.getPoints().isEmpty()) {
                Log.w(TAG, "No route points found in GPX file");
                Toast.makeText(this, "No route points found in GPX file", Toast.LENGTH_LONG).show();
                return false;
            }

            // Calculate basic route information
            double distance = GpxParser.calculateRouteDistance(loadedRoute.getPoints()) / 1000.0;
            boolean hasElevation = GpxParser.hasElevationData(loadedRoute.getPoints());

            String fileName = getFileName(uri);
            String routeName = loadedRoute.getName();

            Log.d(TAG, String.format("GPX loaded - File: %s, Points: %d, Distance: %.2f km",
                    fileName, loadedRoute.getPoints().size(), distance));

            // Build info display
            StringBuilder info = new StringBuilder();
            info.append("File: ").append(fileName).append("\n");
            if (routeName != null && !routeName.trim().isEmpty()) {
                info.append("Route: ").append(routeName).append("\n");
            }
            info.append(String.format("Points: %d\n", loadedRoute.getPoints().size()));
            info.append(String.format("Distance: %.2f km\n", distance));
            info.append("Elevation data: ").append(hasElevation ? "Available" : "Not available").append("\n");

            // Add file source information for shared files
            String scheme = uri.getScheme();
            String authority = uri.getAuthority();
            if ("content".equals(scheme) && authority != null) {
                if (authority.contains("strava")) {
                    info.append("Source: Strava");
                } else if (authority.contains("garmin")) {
                    info.append("Source: Garmin Connect");
                } else if (authority.contains("wahoo")) {
                    info.append("Source: Wahoo");
                } else if (authority.contains("media")) {
                    info.append("Source: Device storage");
                } else {
                    info.append("Source: ").append(authority);
                }
            } else {
                info.append("Source: File system");
            }

            fileInfoText.setText(info.toString());
            analyzeButton.setEnabled(true);

            Log.d(TAG, "GPX file loaded successfully");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error loading GPX file from URI: " + uri, e);
            Toast.makeText(this, "Error loading GPX file: " + e.getMessage(), Toast.LENGTH_LONG).show();

            // Reset UI state on error
            fileInfoText.setText("Error loading GPX file: " + e.getMessage());
            analyzeButton.setEnabled(false);
            loadedRoute = null;
            return false;
        }
    }

    private String getFileName(Uri uri) {
        String fileName = "Unknown file";

        try {
            // Try to get the display name from content resolver
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1 && cursor.getString(nameIndex) != null) {
                    fileName = cursor.getString(nameIndex);
                }
                cursor.close();
            }

            // Fallback to path-based name extraction
            if ("Unknown file".equals(fileName)) {
                String path = uri.getPath();
                if (path != null) {
                    int lastSlash = path.lastIndexOf('/');
                    if (lastSlash != -1 && lastSlash < path.length() - 1) {
                        fileName = path.substring(lastSlash + 1);
                    }
                }
            }

            // Final fallback to URI segment
            if ("Unknown file".equals(fileName)) {
                String lastSegment = uri.getLastPathSegment();
                if (lastSegment != null) {
                    fileName = lastSegment;
                }
            }

            // If still unknown, create a default name
            if ("Unknown file".equals(fileName)) {
                fileName = "shared_route.gpx";
            }

        } catch (Exception e) {
            Log.e(TAG, "Error getting filename", e);
            fileName = "shared_route.gpx";
        }

        // Ensure .gpx extension
        if (!fileName.toLowerCase().endsWith(".gpx")) {
            fileName += ".gpx";
        }

        return fileName;
    }

    // Replace the analyzeRoute() method in GpxAnalyzerActivity.java with this implementation

    // Replace the analyzeRoute() method in GpxAnalyzerActivity.java with this corrected version

    // Replace the analyzeRoute() method in GpxAnalyzerActivity.java with this memory-safe version

    private void analyzeRoute() {
        if (loadedRoute == null || loadedRoute.getPoints().isEmpty()) {
            Toast.makeText(this, "No route loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show progress UI
        analyzeButton.setEnabled(false);
        selectFileButton.setEnabled(false);
        progressBar.setVisibility(ProgressBar.VISIBLE);
        progressText.setVisibility(TextView.VISIBLE);
        progressText.setText("Starting memory-optimized analysis...");

        BikeType currentBikeType = bikeTypeManager.getCurrentBikeType();
        String analysisMessage = "Analyzing for " + currentBikeType.getDisplayName();
        if (bikeTypeManager.shouldFetchElevationData() && !GpxParser.hasElevationData(loadedRoute.getPoints())) {
            analysisMessage += "\nProcessing in chunks to prevent memory issues...";
        }
        progressText.setText(analysisMessage);

        // Use the memory-optimized evaluator to prevent OOM crashes
        MemoryOptimizedGpxEvaluator.analyzeGpxRouteOptimized(loadedRoute.getPoints(), bikeTypeManager,
                new MemoryOptimizedGpxEvaluator.OptimizedRouteAnalysisCallback() {
                    @Override
                    public void onAnalysisComplete(MemoryOptimizedGpxEvaluator.OptimizedRouteAnalysis analysis) {
                        runOnUiThread(() -> {
                            hideProgressUI();

                            Log.d(TAG, String.format("Memory-optimized analysis complete: %.1f%% green, %.1f%% yellow, %.1f%% red, %.1f%% unknown (found %d roads total)",
                                    analysis.greenPercentage, analysis.yellowPercentage,
                                    analysis.redPercentage, analysis.unknownPercentage, analysis.totalRoadsInArea));

                            // Show a simple result dialog (create this method below)
                            showOptimizedAnalysisResults(analysis);
                        });
                    }

                    @Override
                    public void onAnalysisError(String error) {
                        runOnUiThread(() -> {
                            hideProgressUI();
                            Log.e(TAG, "Memory-optimized analysis failed: " + error);
                            Toast.makeText(GpxAnalyzerActivity.this,
                                    "Analysis failed: " + error, Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onProgress(int progress, String message) {
                        runOnUiThread(() -> {
                            progressBar.setProgress(progress);
                            progressText.setText(message);
                        });
                    }
                });
    }

    // Add this method to GpxAnalyzerActivity.java to show results
    private void showOptimizedAnalysisResults(MemoryOptimizedGpxEvaluator.OptimizedRouteAnalysis analysis) {
        MemoryOptimizedGpxAnalysisDialog.show(this, analysis);

        StringBuilder message = new StringBuilder();
        message.append("GPX Route Analysis Results\n");
        message.append("=========================\n\n");
        message.append(String.format("Bike Type: %s %s\n",
                analysis.analyzedForBikeType.getEmoji(),
                analysis.analyzedForBikeType.getDisplayName()));
        message.append(String.format("Total Distance: %.2f km\n", analysis.totalDistance));
        message.append(String.format("Data Coverage: %.1f%%\n\n", analysis.dataCoveragePercentage));

        message.append("Road Quality Breakdown:\n");
        message.append(String.format("🟢 Excellent Roads: %.2f km (%.1f%%)\n",
                analysis.greenDistance, analysis.greenPercentage));
        message.append(String.format("🟡 Decent Roads: %.2f km (%.1f%%)\n",
                analysis.yellowDistance, analysis.yellowPercentage));
        message.append(String.format("🔴 Poor Roads: %.2f km (%.1f%%)\n",
                analysis.redDistance, analysis.redPercentage));
        message.append(String.format("⚪ No Road Match: %.2f km (%.1f%%)\n\n",
                analysis.unknownDistance, analysis.unknownPercentage));

        message.append(String.format("Assessment: %s\n", analysis.getQualityAssessment()));
        message.append(String.format("Elevation: %s\n\n", analysis.getElevationAssessment()));

        message.append(String.format("Roads Found: %d total\n", analysis.totalRoadsInArea));
        message.append("Analysis Method: Memory-optimized chunked processing");

    }

    // Add this helper method for sharing results
    private void shareAnalysisText(String analysisText) {
        android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, analysisText);
        shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "GPX Route Analysis");
        startActivity(android.content.Intent.createChooser(shareIntent, "Share Route Analysis"));
    }

    // Also update the progress text method to handle the new callback format:
    private void updatePolylineProgressText(int progress, String message) {
        progressText.setText(message);
    }

    private void updateEnhancedProgressText(int progress) {
        String message;
        if (progress <= 10) {
            message = "Preparing enhanced analysis...";
        } else if (progress <= 25) {
            message = "Analyzing elevation data...";
        } else if (progress <= 50) {
            message = "Fetching road surface data...";
        } else if (progress <= 90) {
            message = "Scoring roads with same system as Find Gravel...";
        } else {
            message = "Finalizing enhanced analysis...";
        }
        progressText.setText(message);
    }

    private void updateProgressText(int progress) {
        String message;
        if (progress <= 10) {
            message = "Preparing analysis...";
        } else if (progress <= 30) {
            message = "Analyzing elevation data...";
        } else if (progress <= 70) {
            message = "Querying road surface data...";
        } else if (progress <= 90) {
            message = "Processing surface information...";
        } else {
            message = "Finalizing analysis...";
        }
        progressText.setText(message);
    }

    private void hideProgressUI() {
        analyzeButton.setEnabled(true);
        selectFileButton.setEnabled(true);
        progressBar.setVisibility(ProgressBar.GONE);
        progressText.setVisibility(TextView.GONE);
    }

}