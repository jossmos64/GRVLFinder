package be.kuleuven.gt.grvlfinder;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.Marker;
import java.util.Locale;
import java.util.List;

/**
 * Enhanced dialog for displaying Memory Optimized GPX analysis results with maps
 */
public class MemoryOptimizedGpxAnalysisDialog {

    // App theme colors from XML - updated to match your theme
    private static final int BACKGROUND_COLOR = Color.parseColor("#f2ead7");
    private static final int TEXT_COLOR = Color.parseColor("#475e41");
    private static final int ACCENT_COLOR = Color.parseColor("#d0b58a");
    private static final int CARD_BACKGROUND = Color.parseColor("#ffffff");
    private static final int LIGHT_BACKGROUND = Color.parseColor("#faf8f3");

    public static void show(Context context, MemoryOptimizedGpxEvaluator.OptimizedRouteAnalysis analysis) {
        // Initialize osmdroid before using MapView
        org.osmdroid.config.Configuration.getInstance().load(
                context,
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        );

        BottomSheetDialog dialog = new BottomSheetDialog(context);
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(BACKGROUND_COLOR);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(BACKGROUND_COLOR);
        container.setPadding(40, 40, 40, 40);

        // Title with app theme styling
        TextView title = new TextView(context);
        title.setText("Route Analysis");
        title.setTextSize(28f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TEXT_COLOR);
        container.addView(title, createMarginParams(0, 0, 0, 8));

        // Subtitle with bike type
        TextView subtitle = new TextView(context);
        subtitle.setText("Optimized for " + analysis.analyzedForBikeType.getDisplayName());
        subtitle.setTextSize(16f);
        subtitle.setTextColor(ACCENT_COLOR);
        subtitle.setTypeface(Typeface.DEFAULT_BOLD);
        container.addView(subtitle, createMarginParams(0, 0, 0, 24));

        // Basic metrics section
        addSectionCard(container, "Route Metrics", LIGHT_BACKGROUND);
        addMetricView(container, "Total Distance", String.format(Locale.US, "%.2f km", analysis.totalDistance), TEXT_COLOR);
        addMetricView(container, "Data Coverage", String.format(Locale.US, "%.1f%%", analysis.dataCoveragePercentage),
                getCoverageColor(analysis.dataCoveragePercentage));
        addMetricView(container, "Roads Found", String.valueOf(analysis.totalRoadsInArea), TEXT_COLOR);

        // Quality breakdown section
        addSectionCard(container, "Road Quality Breakdown", LIGHT_BACKGROUND);
        addQualityBar(container, "🟢 Excellent Roads", analysis.greenDistance, analysis.greenPercentage,
                Color.parseColor("#4CAF50"), "Perfect for " + analysis.analyzedForBikeType.getDisplayName());
        addQualityBar(container, "🟡 Decent Roads", analysis.yellowDistance, analysis.yellowPercentage,
                Color.parseColor("#FF9800"), "Acceptable quality");
        addQualityBar(container, "🔴 Poor Roads", analysis.redDistance, analysis.redPercentage,
                Color.parseColor("#F44336"), "Challenging sections");
        addQualityBar(container, "⚪ No Road Match", analysis.unknownDistance, analysis.unknownPercentage,
                Color.parseColor("#9E9E9E"), "No surface data available");

        // Route quality visualization map - FIXED IMPLEMENTATION
        addRouteQualityMap(container, context, analysis);

        // Elevation analysis section
        if (analysis.hasElevationData) {
            addElevationCard(container, context, analysis);
        }

        // Route assessment
        addAssessmentCard(container, analysis);

        // Analysis method info
        addAnalysisMethodCard(container, analysis);

        // Action buttons
        addActionButtons(container, context, analysis);

        // Add container to dialog
        scroll.addView(container);
        dialog.setContentView(scroll);

        // Proper MapView lifecycle management
        dialog.setOnShowListener(d -> {
            resumeMapViews(container);
        });

        dialog.setOnDismissListener(d -> {
            pauseMapViews(container);
        });

        dialog.show();
    }

    private static void addRouteQualityMap(LinearLayout container, Context context,
                                           MemoryOptimizedGpxEvaluator.OptimizedRouteAnalysis analysis) {
        TextView mapTitle = new TextView(context);
        mapTitle.setText("Interactive Route Quality Map");
        mapTitle.setTextSize(16f);
        mapTitle.setTypeface(Typeface.DEFAULT_BOLD);
        mapTitle.setTextColor(TEXT_COLOR);
        mapTitle.setPadding(16, 12, 16, 8);
        container.addView(mapTitle);

        // Create MapView with proper initialization
        MapView routeMapView = new MapView(context);
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 600);
        mapParams.setMargins(16, 8, 16, 16);
        routeMapView.setLayoutParams(mapParams);
        routeMapView.setBackgroundColor(CARD_BACKGROUND);

        // Configure map
        routeMapView.setTileSource(TileSourceFactory.MAPNIK);
        routeMapView.setMultiTouchControls(true);
        routeMapView.setBuiltInZoomControls(true);
        routeMapView.getController().setZoom(13.0);

        // Add route segments with color coding - FIXED IMPLEMENTATION
        if (analysis.routeSegments != null && !analysis.routeSegments.isEmpty()) {
            double minLat = Double.MAX_VALUE, maxLat = Double.MIN_VALUE;
            double minLon = Double.MAX_VALUE, maxLon = Double.MIN_VALUE;
            boolean boundsSet = false;

            for (MemoryOptimizedGpxEvaluator.RouteSegmentResult segment : analysis.routeSegments) {
                if (segment.points == null || segment.points.isEmpty()) continue;

                Polyline segmentLine = new Polyline();
                segmentLine.setPoints(segment.points);
                segmentLine.setWidth(8.0f);

                // Color based on quality score - SAME AS MAIN APP
                if (segment.qualityScore >= 20) {
                    segmentLine.setColor(Color.parseColor("#DD228B22")); // Green - excellent
                } else if (segment.qualityScore >= 10) {
                    segmentLine.setColor(Color.parseColor("#DDFFA500")); // Orange - decent
                } else if (segment.qualityScore >= 0) {
                    segmentLine.setColor(Color.parseColor("#CCDC143C")); // Red - poor
                } else {
                    segmentLine.setColor(Color.parseColor("#9E9E9E")); // Gray - unknown
                }

                // Add click listener for segment details
                segmentLine.setOnClickListener((polyline, mapView, eventPos) -> {
                    showSegmentDetails(context, segment);
                    return true;
                });

                routeMapView.getOverlays().add(segmentLine);

                // Calculate bounds - FIXED ALGORITHM
                for (GeoPoint point : segment.points) {
                    double lat = point.getLatitude();
                    double lon = point.getLongitude();

                    if (!boundsSet) {
                        // Initialize bounds with first valid point
                        minLat = maxLat = lat;
                        minLon = maxLon = lon;
                        boundsSet = true;
                    } else {
                        // Expand bounds
                        minLat = Math.min(minLat, lat);
                        maxLat = Math.max(maxLat, lat);
                        minLon = Math.min(minLon, lon);
                        maxLon = Math.max(maxLon, lon);
                    }
                }
            }

            // Set map bounds with padding - FIXED CALCULATION
            if (boundsSet) {
                double latSpan = maxLat - minLat;
                double lonSpan = maxLon - minLon;

                // Add 20% padding around the route
                double latPadding = Math.max(latSpan * 0.2, 0.001); // Minimum padding
                double lonPadding = Math.max(lonSpan * 0.2, 0.001);

                BoundingBox routeBounds = new BoundingBox(
                        maxLat + latPadding, maxLon + lonPadding,
                        minLat - latPadding, minLon - lonPadding
                );

                // Use post to ensure map is fully initialized
                routeMapView.post(() -> {
                    routeMapView.zoomToBoundingBox(routeBounds, false, 100);
                    routeMapView.invalidate();
                });

                android.util.Log.d("RouteMap", String.format(
                        "Setting bounds: lat %.6f to %.6f, lon %.6f to %.6f",
                        minLat - latPadding, maxLat + latPadding,
                        minLon - lonPadding, maxLon + lonPadding));
            }
        }

        container.addView(routeMapView);

        // Map legend
        addMapLegend(container, context);
    }

    private static void showSegmentDetails(Context context, MemoryOptimizedGpxEvaluator.RouteSegmentResult segment) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
        builder.setTitle("Segment Details");

        StringBuilder details = new StringBuilder();
        details.append("Quality: ").append(segment.qualityDescription).append("\n");
        details.append("Score: ").append(segment.qualityScore).append("\n");
        details.append("Distance: ").append(String.format(Locale.US, "%.0fm", segment.distance)).append("\n");
        details.append("Surface: ").append(segment.surfaceType).append("\n");
        if (segment.slope >= 0) {
            details.append("Max Slope: ").append(String.format(Locale.US, "%.1f%%", segment.slope));
        }

        builder.setMessage(details.toString());
        builder.setPositiveButton("OK", null);
        builder.show();
    }

    private static void addMapLegend(LinearLayout container, Context context) {
        TextView legendTitle = new TextView(context);
        legendTitle.setText("Map Legend:");
        legendTitle.setTextSize(14f);
        legendTitle.setTypeface(Typeface.DEFAULT_BOLD);
        legendTitle.setTextColor(TEXT_COLOR);
        legendTitle.setPadding(16, 8, 16, 4);
        container.addView(legendTitle);

        LinearLayout legendContainer = new LinearLayout(context);
        legendContainer.setOrientation(LinearLayout.HORIZONTAL);
        legendContainer.setPadding(16, 0, 16, 16);

        addLegendItem(legendContainer, context, "Excellent", Color.parseColor("#4CAF50"));
        addLegendItem(legendContainer, context, "Decent", Color.parseColor("#FF9800"));
        addLegendItem(legendContainer, context, "Poor", Color.parseColor("#F44336"));
        addLegendItem(legendContainer, context, "Unknown", Color.parseColor("#9E9E9E"));

        container.addView(legendContainer);
    }

    private static void addLegendItem(LinearLayout parent, Context context, String label, int color) {
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setPadding(0, 0, 16, 0);

        // Color box
        TextView colorBox = new TextView(context);
        colorBox.setText("■ ");
        colorBox.setTextSize(16f);
        colorBox.setTextColor(color);

        // Label
        TextView labelText = new TextView(context);
        labelText.setText(label);
        labelText.setTextSize(12f);
        labelText.setTextColor(TEXT_COLOR);
        labelText.setPadding(4, 0, 0, 0);

        item.addView(colorBox);
        item.addView(labelText);
        parent.addView(item);
    }

    private static void addElevationCard(LinearLayout container, Context context,
                                         MemoryOptimizedGpxEvaluator.OptimizedRouteAnalysis analysis) {
        addSectionCard(container, "Elevation Profile", LIGHT_BACKGROUND);

        // Max slope with prominent display
        LinearLayout slopeContainer = new LinearLayout(context);
        slopeContainer.setOrientation(LinearLayout.HORIZONTAL);
        slopeContainer.setPadding(16, 12, 16, 8);
        slopeContainer.setWeightSum(1.0f);

        TextView slopeLabel = new TextView(context);
        slopeLabel.setText("Steepest Slope:");
        slopeLabel.setTextSize(16f);
        slopeLabel.setTextColor(TEXT_COLOR);
        slopeLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.5f));

        TextView slopeValue = new TextView(context);
        slopeValue.setText(String.format(Locale.US, "%.1f%%", analysis.maxSlope));
        slopeValue.setTextSize(24f);
        slopeValue.setTypeface(Typeface.DEFAULT_BOLD);
        slopeValue.setTextColor(getSlopeColor(analysis.maxSlope));
        slopeValue.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.5f));

        slopeContainer.addView(slopeLabel);
        slopeContainer.addView(slopeValue);
        container.addView(slopeContainer);

        // Steepest Point Map - FIXED IMPLEMENTATION
        if (analysis.steepestPoint != null) {
            addSteepestPointMap(container, context, analysis);
        }

        // Location text
        if (analysis.steepestPoint != null && !analysis.steepestLocationDescription.equals("Unknown")) {
            TextView locationText = new TextView(context);
            locationText.setText("Location: " + analysis.steepestLocationDescription);
            locationText.setTextSize(13f);
            locationText.setTextColor(TEXT_COLOR);
            locationText.setPadding(16, 0, 16, 8);
            container.addView(locationText);
        }

        // Assessment
        TextView elevationAssessment = new TextView(context);
        elevationAssessment.setText(analysis.getElevationAssessment());
        elevationAssessment.setTextSize(14f);
        elevationAssessment.setTextColor(getSlopeColor(analysis.maxSlope));
        elevationAssessment.setPadding(16, 4, 16, 16);
        container.addView(elevationAssessment);

        // Warnings for steep slopes
        addSlopeWarnings(container, context, analysis);
    }

    private static void addSteepestPointMap(LinearLayout container, Context context,
                                            MemoryOptimizedGpxEvaluator.OptimizedRouteAnalysis analysis) {
        TextView mapTitle = new TextView(context);
        mapTitle.setText("Steepest Climb Location");
        mapTitle.setTextSize(16f);
        mapTitle.setTypeface(Typeface.DEFAULT_BOLD);
        mapTitle.setTextColor(TEXT_COLOR);
        mapTitle.setPadding(16, 12, 16, 8);
        container.addView(mapTitle);

        // Create MapView for steepest point - FIXED IMPLEMENTATION
        MapView elevationMapView = new MapView(context);
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 400);
        mapParams.setMargins(16, 8, 16, 16);
        elevationMapView.setLayoutParams(mapParams);
        elevationMapView.setBackgroundColor(CARD_BACKGROUND);

        // Configure map
        elevationMapView.setTileSource(TileSourceFactory.MAPNIK);
        elevationMapView.setMultiTouchControls(true);
        elevationMapView.setBuiltInZoomControls(true);
        elevationMapView.getController().setZoom(16.0); // Higher zoom for detail

        // Center on steepest point
        elevationMapView.getController().setCenter(analysis.steepestPoint);

        // Add marker for steepest point - FIXED MARKER IMPLEMENTATION
        Marker steepestMarker = new Marker(elevationMapView);
        steepestMarker.setPosition(analysis.steepestPoint);
        steepestMarker.setTitle(String.format(Locale.US, "Steepest Climb: %.1f%%", analysis.maxSlope));
        steepestMarker.setSnippet("Tap for details");

        // Use a drawable that exists in all Android versions
        steepestMarker.setIcon(context.getResources().getDrawable(android.R.drawable.ic_dialog_alert));

        elevationMapView.getOverlays().add(steepestMarker);

        // Add route line around the steep section if available
        if (analysis.routeSegments != null) {
            // Find segments near the steepest point and highlight them
            for (MemoryOptimizedGpxEvaluator.RouteSegmentResult segment : analysis.routeSegments) {
                if (segment.points == null || segment.points.isEmpty()) continue;

                for (GeoPoint point : segment.points) {
                    double distance = analysis.steepestPoint.distanceToAsDouble(point);
                    if (distance < 500) { // Within 500m of steepest point
                        Polyline contextLine = new Polyline();
                        contextLine.setPoints(segment.points);
                        contextLine.setWidth(6.0f);
                        contextLine.setColor(Color.parseColor("#FF5722")); // Orange-red for context
                        elevationMapView.getOverlays().add(contextLine);
                        break;
                    }
                }
            }
        }

        container.addView(elevationMapView);
    }

    private static void addSlopeWarnings(LinearLayout container, Context context,
                                         MemoryOptimizedGpxEvaluator.OptimizedRouteAnalysis analysis) {
        if (analysis.analyzedForBikeType.name().contains("BIKEPACKING") && analysis.maxSlope > 8.0) {
            TextView warning = new TextView(context);
            warning.setPadding(16, 8, 16, 16);
            warning.setTextSize(14f);
            warning.setTypeface(Typeface.DEFAULT_BOLD);
            warning.setBackgroundColor(LIGHT_BACKGROUND);

            if (analysis.maxSlope > 15.0) {
                warning.setText("⚠️ EXTREME: Very steep slopes detected! Consider alternative route for bikepacking.");
                warning.setTextColor(Color.parseColor("#D32F2F"));
            } else if (analysis.maxSlope > 12.0) {
                warning.setText("⚠️ WARNING: Steep slopes may be challenging with bikepacking gear.");
                warning.setTextColor(Color.parseColor("#F57C00"));
            } else {
                warning.setText("ℹ️ NOTE: Moderate slopes - manageable with proper gearing.");
                warning.setTextColor(TEXT_COLOR);
            }

            container.addView(warning);
        }
    }

    // Helper methods to manage MapView lifecycle properly
    private static void resumeMapViews(ViewGroup container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof MapView) {
                ((MapView) child).onResume();
            } else if (child instanceof ViewGroup) {
                resumeMapViews((ViewGroup) child);
            }
        }
    }

    private static void pauseMapViews(ViewGroup container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof MapView) {
                ((MapView) child).onPause();
            } else if (child instanceof ViewGroup) {
                pauseMapViews((ViewGroup) child);
            }
        }
    }

    private static void addSectionCard(LinearLayout container, String title, int backgroundColor) {
        LinearLayout cardHeader = new LinearLayout(container.getContext());
        cardHeader.setOrientation(LinearLayout.HORIZONTAL);
        cardHeader.setBackgroundColor(backgroundColor);
        cardHeader.setPadding(16, 12, 16, 12);

        TextView sectionTitle = new TextView(container.getContext());
        sectionTitle.setText(title);
        sectionTitle.setTextSize(18f);
        sectionTitle.setTypeface(Typeface.DEFAULT_BOLD);
        sectionTitle.setTextColor(TEXT_COLOR);

        cardHeader.addView(sectionTitle);
        container.addView(cardHeader, createMarginParams(0, 20, 0, 0));
    }

    private static void addQualityBar(LinearLayout container, String label, double distance,
                                      double percentage, int color, String description) {
        Context context = container.getContext();

        LinearLayout qualityItem = new LinearLayout(context);
        qualityItem.setOrientation(LinearLayout.VERTICAL);
        qualityItem.setPadding(16, 12, 16, 12);
        qualityItem.setBackgroundColor(CARD_BACKGROUND);

        // Label and value row
        LinearLayout labelRow = new LinearLayout(context);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setWeightSum(1.0f);

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(16f);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        labelView.setTextColor(color);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.6f));

        TextView valueView = new TextView(context);
        valueView.setText(String.format(Locale.US, "%.2f km", distance));
        valueView.setTextSize(16f);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setTextColor(color);
        valueView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.4f));

        labelRow.addView(labelView);
        labelRow.addView(valueView);
        qualityItem.addView(labelRow);

        // Percentage bar
        LinearLayout barContainer = new LinearLayout(context);
        barContainer.setOrientation(LinearLayout.HORIZONTAL);
        barContainer.setBackgroundColor(LIGHT_BACKGROUND);
        barContainer.setPadding(0, 8, 0, 8);

        LinearLayout filledBar = new LinearLayout(context);
        filledBar.setBackgroundColor(color);
        int fillWidth = (int) (300 * percentage / 100);
        LinearLayout.LayoutParams fillParams = new LinearLayout.LayoutParams(fillWidth, 8);
        filledBar.setLayoutParams(fillParams);
        barContainer.addView(filledBar);

        qualityItem.addView(barContainer, createMarginParams(0, 4, 0, 4));

        TextView percentageText = new TextView(context);
        percentageText.setText(String.format(Locale.US, "%.1f%% - %s", percentage, description));
        percentageText.setTextSize(13f);
        percentageText.setTextColor(TEXT_COLOR);
        qualityItem.addView(percentageText);

        container.addView(qualityItem, createMarginParams(0, 2, 0, 2));
    }

    private static void addAssessmentCard(LinearLayout container, MemoryOptimizedGpxEvaluator.OptimizedRouteAnalysis analysis) {
        addSectionCard(container, "Route Assessment", LIGHT_BACKGROUND);

        TextView assessmentText = new TextView(container.getContext());
        assessmentText.setText(analysis.getQualityAssessment());
        assessmentText.setTextSize(16f);
        assessmentText.setTypeface(Typeface.DEFAULT_BOLD);
        assessmentText.setPadding(16, 12, 16, 16);
        assessmentText.setLineSpacing(4, 1.2f);

        // Color code assessment
        if (analysis.greenPercentage >= 60) {
            assessmentText.setTextColor(Color.parseColor("#2E7D32"));
        } else if (analysis.greenPercentage + analysis.yellowPercentage >= 70) {
            assessmentText.setTextColor(Color.parseColor("#F57C00"));
        } else {
            assessmentText.setTextColor(Color.parseColor("#C62828"));
        }

        container.addView(assessmentText);
    }

    private static void addAnalysisMethodCard(LinearLayout container, MemoryOptimizedGpxEvaluator.OptimizedRouteAnalysis analysis) {
        addSectionCard(container, "Analysis Method", LIGHT_BACKGROUND);

        TextView methodText = new TextView(container.getContext());
        String methodDescription = String.format(Locale.US,
                "Memory-Optimized Analysis:\n\n" +
                        "• Processes route in small chunks to prevent crashes\n" +
                        "• Analyzed %d roads across %d segments\n" +
                        "• Uses same scoring system as Find Gravel\n" +
                        "• Interactive maps show quality and elevation details\n\n" +
                        "This method prioritizes consistent results and visual representation, " +
                        "making it suitable for large routes and comprehensive analysis.",
                analysis.totalRoadsInArea, analysis.totalSegmentsAnalyzed);

        methodText.setText(methodDescription);
        methodText.setTextSize(13f);
        methodText.setTextColor(TEXT_COLOR);
        methodText.setLineSpacing(4, 1.2f);
        methodText.setPadding(16, 8, 16, 16);
        container.addView(methodText);
    }

    private static void addActionButtons(LinearLayout container, Context context,
                                         MemoryOptimizedGpxEvaluator.OptimizedRouteAnalysis analysis) {
        LinearLayout buttonContainer = new LinearLayout(context);
        buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
        buttonContainer.setPadding(16, 24, 16, 8);
        buttonContainer.setWeightSum(2.0f);

        // Share button
        Button shareButton = new Button(context);
        shareButton.setText("Share Analysis");
        shareButton.setTextSize(14f);
        shareButton.setTextColor(Color.WHITE);
        shareButton.setBackgroundColor(ACCENT_COLOR);
        shareButton.setOnClickListener(v -> shareAnalysis(context, analysis));

        // Export button (placeholder for future GPX export functionality)
        Button exportButton = new Button(context);
        exportButton.setText("Export Report");
        exportButton.setTextSize(14f);
        exportButton.setTextColor(TEXT_COLOR);
        exportButton.setBackgroundColor(LIGHT_BACKGROUND);
        exportButton.setOnClickListener(v -> {
            android.widget.Toast.makeText(context, "Export feature coming soon!", android.widget.Toast.LENGTH_SHORT).show();
        });

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        buttonParams.setMargins(4, 0, 4, 0);
        shareButton.setLayoutParams(buttonParams);
        exportButton.setLayoutParams(buttonParams);

        buttonContainer.addView(shareButton);
        buttonContainer.addView(exportButton);
        container.addView(buttonContainer);
    }

    private static void addMetricView(LinearLayout container, String label, String value, int color) {
        LinearLayout metricLayout = new LinearLayout(container.getContext());
        metricLayout.setOrientation(LinearLayout.HORIZONTAL);
        metricLayout.setWeightSum(1.0f);
        metricLayout.setPadding(16, 8, 16, 8);

        TextView labelView = new TextView(container.getContext());
        labelView.setText(label + ":");
        labelView.setTextSize(15f);
        labelView.setTextColor(TEXT_COLOR);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.6f));

        TextView valueView = new TextView(container.getContext());
        valueView.setText(value);
        valueView.setTextSize(15f);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setTextColor(color);
        valueView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.4f));

        metricLayout.addView(labelView);
        metricLayout.addView(valueView);
        container.addView(metricLayout);
    }

    private static void shareAnalysis(Context context, MemoryOptimizedGpxEvaluator.OptimizedRouteAnalysis analysis) {
        StringBuilder shareText = new StringBuilder();
        shareText.append("GPX Route Analysis Results\n");
        shareText.append("=========================\n\n");
        shareText.append(String.format("Bike Type: %s\n", analysis.analyzedForBikeType.getDisplayName()));
        shareText.append(String.format("Total Distance: %.2f km\n", analysis.totalDistance));
        shareText.append(String.format("Data Coverage: %.1f%%\n\n", analysis.dataCoveragePercentage));

        shareText.append("Road Quality:\n");
        shareText.append(String.format("🟢 Excellent: %.2f km (%.1f%%)\n", analysis.greenDistance, analysis.greenPercentage));
        shareText.append(String.format("🟡 Decent: %.2f km (%.1f%%)\n", analysis.yellowDistance, analysis.yellowPercentage));
        shareText.append(String.format("🔴 Poor: %.2f km (%.1f%%)\n", analysis.redDistance, analysis.redPercentage));
        shareText.append(String.format("⚪ Unknown: %.2f km (%.1f%%)\n\n", analysis.unknownDistance, analysis.unknownPercentage));

        shareText.append(String.format("Assessment: %s\n", analysis.getQualityAssessment()));
        shareText.append(String.format("Elevation: %s\n\n", analysis.getElevationAssessment()));

        if (analysis.steepestPoint != null) {
            shareText.append(String.format("Steepest climb: %.1f%% at %.6f, %.6f\n\n",
                    analysis.maxSlope,
                    analysis.steepestPoint.getLatitude(),
                    analysis.steepestPoint.getLongitude()));
        }

        shareText.append("Generated by GRVL Finder");

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "GPX Route Analysis");

        context.startActivity(Intent.createChooser(shareIntent, "Share Route Analysis"));
    }

    // Helper methods
    private static int getCoverageColor(double coverage) {
        if (coverage >= 80) return Color.parseColor("#4CAF50");
        else if (coverage >= 60) return Color.parseColor("#8BC34A");
        else if (coverage >= 40) return Color.parseColor("#FF9800");
        else return Color.parseColor("#F44336");
    }

    private static int getSlopeColor(double slope) {
        if (slope > 15.0) return Color.parseColor("#D32F2F");
        else if (slope > 12.0) return Color.parseColor("#F57C00");
        else if (slope > 8.0) return Color.parseColor("#FFA000");
        else if (slope > 5.0) return Color.parseColor("#1976D2");
        else return Color.parseColor("#4CAF50");
    }

    private static LinearLayout.LayoutParams createMarginParams(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(left, top, right, bottom);
        return params;
    }
}