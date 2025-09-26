package be.kuleuven.gt.grvlfinder;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.Locale;
import java.util.Map;

public class GpxAnalysisDialog {

    public static void show(Context context, GpxEvaluator.RouteAnalysis analysis) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        ScrollView scroll = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(40, 40, 40, 40);

        // Title
        TextView title = new TextView(context);
        title.setText("GPX Route Analysis");
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.parseColor("#2E7D32"));
        container.addView(title, createMarginParams(0, 0, 0, 20));

        // Bike type indicator
        TextView bikeTypeView = new TextView(context);
        bikeTypeView.setText("Analyzed for: " + analysis.analyzedForBikeType.getDisplayName());
        bikeTypeView.setTextSize(14f);
        bikeTypeView.setTextColor(Color.parseColor("#666666"));
        container.addView(bikeTypeView, createMarginParams(0, 0, 0, 15));

        // Total distance
        addMetricView(container, "Total Distance",
                String.format(Locale.US, "%.2f km", analysis.totalDistance),
                Color.parseColor("#1976D2"));

        // Surface breakdown section
        TextView surfaceTitle = new TextView(context);
        surfaceTitle.setText("Surface Analysis");
        surfaceTitle.setTextSize(16f);
        surfaceTitle.setTypeface(Typeface.DEFAULT_BOLD);
        surfaceTitle.setTextColor(Color.parseColor("#424242"));
        container.addView(surfaceTitle, createMarginParams(0, 20, 0, 10));

        // Gravel vs Asphalt breakdown based on bike type preference
        if (analysis.analyzedForBikeType.name().contains("ROAD")) {
            // For road bikes, show asphalt preference
            addMetricView(container, "Good Asphalt Roads",
                    String.format(Locale.US, "%.2f km (%.1f%%)", analysis.asphaltDistance, analysis.getAsphaltPercentage()),
                    Color.parseColor("#4CAF50"));

            addMetricView(container, "Gravel/Unpaved",
                    String.format(Locale.US, "%.2f km (%.1f%%)", analysis.gravelDistance, analysis.getGravelPercentage()),
                    analysis.gravelDistance > analysis.asphaltDistance ? Color.parseColor("#FF5722") : Color.parseColor("#FF9800"));
        } else {
            // For gravel bikes, show gravel preference
            addMetricView(container, "Gravel Roads",
                    String.format(Locale.US, "%.2f km (%.1f%%)", analysis.gravelDistance, analysis.getGravelPercentage()),
                    Color.parseColor("#4CAF50"));

            addMetricView(container, "Asphalt/Paved",
                    String.format(Locale.US, "%.2f km (%.1f%%)", analysis.asphaltDistance, analysis.getAsphaltPercentage()),
                    Color.parseColor("#2196F3"));
        }

        if (analysis.unknownSurfaceDistance > 0.1) {
            addMetricView(container, "Unknown Surface",
                    String.format(Locale.US, "%.2f km (%.1f%%)", analysis.unknownSurfaceDistance,
                            (analysis.unknownSurfaceDistance / analysis.totalDistance) * 100.0),
                    Color.parseColor("#9E9E9E"));
        }

        // Elevation analysis section
        if (analysis.hasElevationData || analysis.maxSlope > 0) {
            TextView elevationTitle = new TextView(context);
            elevationTitle.setText("Elevation Analysis");
            elevationTitle.setTextSize(16f);
            elevationTitle.setTypeface(Typeface.DEFAULT_BOLD);
            elevationTitle.setTextColor(Color.parseColor("#424242"));
            container.addView(elevationTitle, createMarginParams(0, 20, 0, 10));

            // Max slope
            String slopeText = String.format(Locale.US, "%.1f%%", analysis.maxSlope);
            int slopeColor = getSlopeColor(analysis.maxSlope);
            addMetricView(container, "Steepest Slope", slopeText, slopeColor);

            // Steepest location
            if (analysis.steepestPoint != null) {
                addMetricView(container, "Steepest Location",
                        analysis.steepestLocationDescription,
                        Color.parseColor("#666666"));
            }

            // Slope warnings for bikepacking modes
            if (analysis.analyzedForBikeType.name().contains("BIKEPACKING") && analysis.maxSlope > 8.0) {
                TextView warning = new TextView(context);
                if (analysis.maxSlope > 15.0) {
                    warning.setText("⚠️ EXTREME: Very steep slopes detected! Consider alternative route.");
                    warning.setTextColor(Color.parseColor("#D32F2F"));
                } else if (analysis.maxSlope > 12.0) {
                    warning.setText("⚠️ WARNING: Steep slopes may be challenging with bikepacking gear.");
                    warning.setTextColor(Color.parseColor("#F57C00"));
                } else {
                    warning.setText("ℹ️ NOTE: Moderate slopes - manageable with proper gearing.");
                    warning.setTextColor(Color.parseColor("#1976D2"));
                }
                warning.setTextSize(13f);
                warning.setTypeface(Typeface.DEFAULT_BOLD);
                container.addView(warning, createMarginParams(10, 15, 10, 5));
            }
        } else {
            TextView noElevationNote = new TextView(context);
            noElevationNote.setText("ℹ️ No elevation data available in GPX file");
            noElevationNote.setTextSize(13f);
            noElevationNote.setTextColor(Color.parseColor("#757575"));
            container.addView(noElevationNote, createMarginParams(0, 15, 0, 5));
        }

        // Data coverage information
        if (analysis.totalSegmentsAnalyzed > 0) {
            TextView dataTitle = new TextView(context);
            dataTitle.setText("Analysis Coverage");
            dataTitle.setTextSize(16f);
            dataTitle.setTypeface(Typeface.DEFAULT_BOLD);
            dataTitle.setTextColor(Color.parseColor("#424242"));
            container.addView(dataTitle, createMarginParams(0, 20, 0, 10));

            double coverage = analysis.getDataCoveragePercentage();
            String coverageText = String.format(Locale.US, "%.1f%% (%d/%d segments)",
                    coverage, analysis.segmentsWithRoadData, analysis.totalSegmentsAnalyzed);

            int coverageColor;
            if (coverage >= 70) coverageColor = Color.parseColor("#4CAF50");
            else if (coverage >= 40) coverageColor = Color.parseColor("#FF9800");
            else coverageColor = Color.parseColor("#F44336");

            addMetricView(container, "Road Data Coverage", coverageText, coverageColor);

            if (coverage < 50) {
                TextView coverageNote = new TextView(context);
                coverageNote.setText("ℹ️ Low data coverage - results may be incomplete. Route may use paths not in OpenStreetMap.");
                coverageNote.setTextSize(12f);
                coverageNote.setTextColor(Color.parseColor("#757575"));
                container.addView(coverageNote, createMarginParams(10, 10, 10, 5));
            }
        }

        // Detailed surface breakdown
        if (!analysis.surfaceBreakdown.isEmpty()) {
            TextView detailTitle = new TextView(context);
            detailTitle.setText("Detailed Surface Types");
            detailTitle.setTextSize(16f);
            detailTitle.setTypeface(Typeface.DEFAULT_BOLD);
            detailTitle.setTextColor(Color.parseColor("#424242"));
            container.addView(detailTitle, createMarginParams(0, 20, 0, 10));

            for (Map.Entry<String, Double> entry : analysis.surfaceBreakdown.entrySet()) {
                if (entry.getValue() > 0.05) { // Only show surfaces with > 50m
                    String surfaceName = entry.getKey().replace("_", " ");
                    surfaceName = surfaceName.substring(0, 1).toUpperCase() + surfaceName.substring(1);

                    String distanceText = String.format(Locale.US, "%.2f km", entry.getValue());
                    addDetailView(container, surfaceName, distanceText);
                }
            }
        }

        // Route suitability summary
        addSuitabilitySummary(container, analysis);

        scroll.addView(container);
        dialog.setContentView(scroll);
        dialog.show();
    }

    private static void addMetricView(LinearLayout container, String label, String value, int color) {
        LinearLayout metricLayout = new LinearLayout(container.getContext());
        metricLayout.setOrientation(LinearLayout.HORIZONTAL);
        metricLayout.setWeightSum(1.0f);

        TextView labelView = new TextView(container.getContext());
        labelView.setText(label + ":");
        labelView.setTextSize(14f);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.6f));

        TextView valueView = new TextView(container.getContext());
        valueView.setText(value);
        valueView.setTextSize(14f);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setTextColor(color);
        valueView.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.4f));

        metricLayout.addView(labelView);
        metricLayout.addView(valueView);

        container.addView(metricLayout, createMarginParams(0, 0, 0, 8));
    }

    private static void addDetailView(LinearLayout container, String label, String value) {
        LinearLayout detailLayout = new LinearLayout(container.getContext());
        detailLayout.setOrientation(LinearLayout.HORIZONTAL);
        detailLayout.setWeightSum(1.0f);
        detailLayout.setPadding(20, 0, 0, 0);

        TextView labelView = new TextView(container.getContext());
        labelView.setText(label);
        labelView.setTextSize(12f);
        labelView.setTextColor(Color.parseColor("#666666"));
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f));

        TextView valueView = new TextView(container.getContext());
        valueView.setText(value);
        valueView.setTextSize(12f);
        valueView.setTextColor(Color.parseColor("#424242"));
        valueView.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.3f));

        detailLayout.addView(labelView);
        detailLayout.addView(valueView);

        container.addView(detailLayout, createMarginParams(0, 0, 0, 4));
    }

    private static void addSuitabilitySummary(LinearLayout container, GpxEvaluator.RouteAnalysis analysis) {
        TextView summaryTitle = new TextView(container.getContext());
        summaryTitle.setText("Route Suitability");
        summaryTitle.setTextSize(16f);
        summaryTitle.setTypeface(Typeface.DEFAULT_BOLD);
        summaryTitle.setTextColor(Color.parseColor("#424242"));
        container.addView(summaryTitle, createMarginParams(0, 25, 0, 10));

        TextView summaryText = new TextView(container.getContext());
        String summary = generateSuitabilitySummary(analysis);
        summaryText.setText(summary);
        summaryText.setTextSize(13f);
        summaryText.setTextColor(Color.parseColor("#424242"));
        summaryText.setLineSpacing(4, 1.2f);
        container.addView(summaryText, createMarginParams(0, 0, 0, 10));
    }

    private static String generateSuitabilitySummary(GpxEvaluator.RouteAnalysis analysis) {
        StringBuilder summary = new StringBuilder();

        BikeType bikeType = analysis.analyzedForBikeType;

        switch (bikeType) {
            case RACE_ROAD:
                if (analysis.getAsphaltPercentage() > 80) {
                    summary.append("✅ Excellent for road cycling - mostly paved surfaces.");
                } else if (analysis.getAsphaltPercentage() > 60) {
                    summary.append("⚠️ Mostly suitable for road cycling, but contains some unpaved sections.");
                } else {
                    summary.append("❌ Not ideal for road cycling - significant unpaved sections.");
                }
                break;

            case GRAVEL_BIKE:
                if (analysis.getGravelPercentage() > 60) {
                    summary.append("✅ Perfect for gravel biking - good mix of surfaces.");
                } else if (analysis.getGravelPercentage() > 30) {
                    summary.append("✅ Good for gravel biking with some paved sections.");
                } else {
                    summary.append("⚠️ Limited gravel - mostly paved route.");
                }
                break;

            case RACE_BIKEPACKING:
                summary.append("🚴‍♂️ Road touring analysis: ");
                if (analysis.getAsphaltPercentage() > 70 && analysis.maxSlope < 8.0) {
                    summary.append("✅ Excellent for bikepacking - good roads with manageable slopes.");
                } else if (analysis.maxSlope > 12.0) {
                    summary.append("❌ Contains very steep sections that may be difficult with loaded bike.");
                } else {
                    summary.append("⚠️ Moderate difficulty - check gear ratios for hills.");
                }
                break;

            case GRAVEL_BIKEPACKING:
                summary.append("🎒 Adventure touring analysis: ");
                if (analysis.getGravelPercentage() > 40 && analysis.maxSlope < 10.0) {
                    summary.append("✅ Great adventure route with manageable terrain.");
                } else if (analysis.maxSlope > 15.0) {
                    summary.append("❌ Extreme terrain - only for experienced bikepackers.");
                } else {
                    summary.append("⚠️ Challenging route - prepare for varied conditions.");
                }
                break;

            case CUSTOM:
                summary.append("⚙️ Custom analysis complete - review detailed metrics above.");
                break;
        }

        if (analysis.getDataCoveragePercentage() < 50) {
            summary.append("\n\nⓘ Limited road data available - actual conditions may vary.");
        }

        return summary.toString();
    }

    private static int getSlopeColor(double slope) {
        if (slope > 15.0) return Color.parseColor("#D32F2F");      // Red - extreme
        else if (slope > 12.0) return Color.parseColor("#F57C00");  // Orange - steep
        else if (slope > 8.0) return Color.parseColor("#FFA000");   // Amber - moderate
        else if (slope > 5.0) return Color.parseColor("#1976D2");   // Blue - gentle
        else return Color.parseColor("#4CAF50");                    // Green - flat
    }

    private static LinearLayout.LayoutParams createMarginParams(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(left, top, right, bottom);
        return params;
    }
}