package be.kuleuven.gt.grvlfinder;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.Locale;
import java.util.Map;

/**
 * Dialog for displaying fast accurate GPX analysis results
 */
public class FastAccurateGpxAnalysisDialog {

    public static void show(Context context, FastAccurateGpxEvaluator.FastAccurateRouteAnalysis analysis) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        ScrollView scroll = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(40, 40, 40, 40);

        // Title
        TextView title = new TextView(context);
        title.setText("GPX Route Analysis (Fast & Accurate)");
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

        // Performance metrics section
        TextView performanceTitle = new TextView(context);
        performanceTitle.setText("Analysis Performance");
        performanceTitle.setTextSize(16f);
        performanceTitle.setTypeface(Typeface.DEFAULT_BOLD);
        performanceTitle.setTextColor(Color.parseColor("#424242"));
        container.addView(performanceTitle, createMarginParams(0, 20, 0, 10));

        addMetricView(container, "Data Coverage",
                String.format(Locale.US, "%.1f%% (%d/%d segments)",
                        analysis.dataCoveragePercentage, analysis.segmentsWithRoadData, analysis.totalSegmentsAnalyzed),
                getCoverageColor(analysis.dataCoveragePercentage));

        addMetricView(container, "Roads Found in Area",
                String.valueOf(analysis.totalRoadsInArea),
                Color.parseColor("#1976D2"));

        addMetricView(container, "Roads Actually Matched",
                String.valueOf(analysis.totalRoadsMatched),
                Color.parseColor("#4CAF50"));

        // Efficiency note
        if (analysis.totalRoadsMatched > 0 && analysis.totalRoadsInArea > 0) {
            double efficiency = (analysis.totalRoadsMatched / (double) analysis.totalRoadsInArea) * 100.0;
            TextView efficiencyNote = new TextView(context);
            efficiencyNote.setText(String.format(Locale.US,
                    "Memory Efficiency: Used %.1f%% of available road data", efficiency));
            efficiencyNote.setTextSize(12f);
            efficiencyNote.setTextColor(Color.parseColor("#666666"));
            container.addView(efficiencyNote, createMarginParams(0, 5, 0, 15));
        }

        // Road quality breakdown section
        TextView qualityTitle = new TextView(context);
        qualityTitle.setText("Road Quality Breakdown");
        qualityTitle.setTextSize(16f);
        qualityTitle.setTypeface(Typeface.DEFAULT_BOLD);
        qualityTitle.setTextColor(Color.parseColor("#424242"));
        container.addView(qualityTitle, createMarginParams(0, 20, 0, 10));

        // Green roads (excellent)
        addMetricView(container, "Excellent Roads (Score ≥ 20)",
                String.format(Locale.US, "%.2f km (%.1f%%)", analysis.greenDistance, analysis.greenPercentage),
                Color.parseColor("#4CAF50"));

        // Yellow roads (decent)
        addMetricView(container, "Decent Roads (Score 10-19)",
                String.format(Locale.US, "%.2f km (%.1f%%)", analysis.yellowDistance, analysis.yellowPercentage),
                Color.parseColor("#FF9800"));

        // Red roads (poor)
        addMetricView(container, "Poor Roads (Score < 10)",
                String.format(Locale.US, "%.2f km (%.1f%%)", analysis.redDistance, analysis.redPercentage),
                Color.parseColor("#F44336"));

        // Unknown/no match
        if (analysis.unknownDistance > 0.1) {
            addMetricView(container, "No Road Match",
                    String.format(Locale.US, "%.2f km (%.1f%%)", analysis.unknownDistance, analysis.unknownPercentage),
                    Color.parseColor("#9E9E9E"));
        }

        // Quality assessment
        addQualityAssessmentSection(container, analysis);

        // Elevation analysis section
        if (analysis.hasElevationData || analysis.maxSlope > 0) {
            addElevationSection(container, analysis);
        } else {
            TextView noElevationNote = new TextView(context);
            noElevationNote.setText("ℹ️ No elevation data available in GPX file");
            noElevationNote.setTextSize(13f);
            noElevationNote.setTextColor(Color.parseColor("#757575"));
            container.addView(noElevationNote, createMarginParams(0, 15, 0, 5));
        }

        // Analysis method section
        addAnalysisMethodSection(container, analysis);

        // Bike type compatibility section
        addBikeTypeCompatibilitySection(container, analysis);

        // Surface breakdown (if available)
        if (!analysis.surfaceBreakdown.isEmpty()) {
            addSurfaceBreakdownSection(container, analysis);
        }

        // Share button
        addShareButton(container, context, analysis);

        scroll.addView(container);
        dialog.setContentView(scroll);
        dialog.show();
    }

    private static void addQualityAssessmentSection(LinearLayout container,
                                                    FastAccurateGpxEvaluator.FastAccurateRouteAnalysis analysis) {
        TextView assessmentTitle = new TextView(container.getContext());
        assessmentTitle.setText("Route Assessment");
        assessmentTitle.setTextSize(16f);
        assessmentTitle.setTypeface(Typeface.DEFAULT_BOLD);
        assessmentTitle.setTextColor(Color.parseColor("#424242"));
        container.addView(assessmentTitle, createMarginParams(0, 20, 0, 10));

        TextView assessmentText = new TextView(container.getContext());
        assessmentText.setText(analysis.getQualityAssessment());
        assessmentText.setTextSize(14f);
        assessmentText.setLineSpacing(4, 1.2f);

        // Color code assessment
        if (analysis.greenPercentage >= 60) {
            assessmentText.setTextColor(Color.parseColor("#4CAF50"));
        } else if (analysis.greenPercentage + analysis.yellowPercentage >= 70) {
            assessmentText.setTextColor(Color.parseColor("#FF9800"));
        } else {
            assessmentText.setTextColor(Color.parseColor("#F44336"));
        }

        container.addView(assessmentText, createMarginParams(0, 0, 0, 10));
    }

    private static void addElevationSection(LinearLayout container,
                                            FastAccurateGpxEvaluator.FastAccurateRouteAnalysis analysis) {
        TextView elevationTitle = new TextView(container.getContext());
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

        // Elevation assessment
        TextView elevationAssessment = new TextView(container.getContext());
        elevationAssessment.setText(analysis.getElevationAssessment());
        elevationAssessment.setTextSize(13f);
        elevationAssessment.setTextColor(slopeColor);
        container.addView(elevationAssessment, createMarginParams(0, 5, 0, 10));

        // Slope warnings for bikepacking modes
        if (analysis.analyzedForBikeType.name().contains("BIKEPACKING") && analysis.maxSlope > 8.0) {
            TextView warning = new TextView(container.getContext());
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
    }

    private static void addAnalysisMethodSection(LinearLayout container,
                                                 FastAccurateGpxEvaluator.FastAccurateRouteAnalysis analysis) {
        TextView methodTitle = new TextView(container.getContext());
        methodTitle.setText("Analysis Method");
        methodTitle.setTextSize(16f);
        methodTitle.setTypeface(Typeface.DEFAULT_BOLD);
        methodTitle.setTextColor(Color.parseColor("#424242"));
        container.addView(methodTitle, createMarginParams(0, 20, 0, 10));

        TextView methodDescription = new TextView(container.getContext());
        String description = String.format(Locale.US,
                "Fast & Accurate Hybrid Analysis:\n\n" +
                        "• Memory-efficient chunking (prevents crashes)\n" +
                        "• High-accuracy road matching with direction analysis\n" +
                        "• Smart overlapping boundaries for better edge matching\n" +
                        "• Selective elevation fetching (only for matched roads)\n" +
                        "• Used same scoring system as Find Gravel\n\n" +
                        "Performance: Analyzed %d total roads, efficiently matched to %d roads actually used by your route.",
                analysis.totalRoadsInArea, analysis.totalRoadsMatched);

        methodDescription.setText(description);
        methodDescription.setTextSize(12f);
        methodDescription.setTextColor(Color.parseColor("#666666"));
        methodDescription.setLineSpacing(4, 1.2f);
        container.addView(methodDescription, createMarginParams(0, 0, 0, 10));
    }

    private static void addBikeTypeCompatibilitySection(LinearLayout container,
                                                        FastAccurateGpxEvaluator.FastAccurateRouteAnalysis analysis) {
        TextView compatibilityTitle = new TextView(container.getContext());
        compatibilityTitle.setText("Bike Type Compatibility");
        compatibilityTitle.setTextSize(16f);
        compatibilityTitle.setTypeface(Typeface.DEFAULT_BOLD);
        compatibilityTitle.setTextColor(Color.parseColor("#424242"));
        container.addView(compatibilityTitle, createMarginParams(0, 20, 0, 10));

        BikeType bikeType = analysis.analyzedForBikeType;

        // Show relevant metrics based on bike type
        if (bikeType.name().contains("ROAD")) {
            // For road bikes, focus on excellent roads percentage
            addMetricView(container, "High Quality Roads",
                    String.format(Locale.US, "%.1f%%", analysis.greenPercentage),
                    analysis.greenPercentage >= 60 ? Color.parseColor("#4CAF50") :
                            analysis.greenPercentage >= 40 ? Color.parseColor("#FF9800") : Color.parseColor("#F44336"));

            TextView roadAdvice = new TextView(container.getContext());
            if (analysis.greenPercentage >= 70) {
                roadAdvice.setText("✅ Excellent for road cycling - predominantly high-quality surfaces");
                roadAdvice.setTextColor(Color.parseColor("#4CAF50"));
            } else if (analysis.greenPercentage >= 50) {
                roadAdvice.setText("✅ Good for road cycling with some challenging sections");
                roadAdvice.setTextColor(Color.parseColor("#FF9800"));
            } else {
                roadAdvice.setText("⚠️ Limited high-quality roads - consider gravel bike alternative");
                roadAdvice.setTextColor(Color.parseColor("#F57C00"));
            }
            roadAdvice.setTextSize(12f);
            container.addView(roadAdvice, createMarginParams(10, 5, 10, 10));

        } else if (bikeType.name().contains("GRAVEL")) {
            // For gravel bikes, show combined good roads
            double goodRoads = analysis.greenPercentage + analysis.yellowPercentage;
            addMetricView(container, "Suitable Roads (Green + Yellow)",
                    String.format(Locale.US, "%.1f%%", goodRoads),
                    goodRoads >= 70 ? Color.parseColor("#4CAF50") :
                            goodRoads >= 50 ? Color.parseColor("#FF9800") : Color.parseColor("#F44336"));

            TextView gravelAdvice = new TextView(container.getContext());
            if (goodRoads >= 80) {
                gravelAdvice.setText("✅ Outstanding for gravel biking - excellent surface variety");
                gravelAdvice.setTextColor(Color.parseColor("#4CAF50"));
            } else if (goodRoads >= 60) {
                gravelAdvice.setText("✅ Very good for gravel biking with good surface mix");
                gravelAdvice.setTextColor(Color.parseColor("#4CAF50"));
            } else {
                gravelAdvice.setText("⚠️ Mixed suitability - many challenging sections present");
                gravelAdvice.setTextColor(Color.parseColor("#FF9800"));
            }
            gravelAdvice.setTextSize(12f);
            container.addView(gravelAdvice, createMarginParams(10, 5, 10, 10));
        }
    }

    private static void addSurfaceBreakdownSection(LinearLayout container,
                                                   FastAccurateGpxEvaluator.FastAccurateRouteAnalysis analysis) {
        TextView surfaceTitle = new TextView(container.getContext());
        surfaceTitle.setText("Surface Type Details");
        surfaceTitle.setTextSize(16f);
        surfaceTitle.setTypeface(Typeface.DEFAULT_BOLD);
        surfaceTitle.setTextColor(Color.parseColor("#424242"));
        container.addView(surfaceTitle, createMarginParams(0, 20, 0, 10));

        // Show major surface categories
        for (Map.Entry<String, Double> entry : analysis.surfaceBreakdown.entrySet()) {
            if (entry.getValue() > 0.05) { // Only show surfaces with > 50m
                String surfaceName = formatSurfaceName(entry.getKey());
                String distanceText = String.format(Locale.US, "%.2f km", entry.getValue());
                addDetailView(container, surfaceName, distanceText);
            }
        }
    }

    private static void addShareButton(LinearLayout container, Context context,
                                       FastAccurateGpxEvaluator.FastAccurateRouteAnalysis analysis) {
        TextView shareButton = new TextView(context);
        shareButton.setText("📤 Share Analysis");
        shareButton.setTextSize(16f);
        shareButton.setTextColor(Color.parseColor("#1976D2"));
        shareButton.setTypeface(Typeface.DEFAULT_BOLD);
        shareButton.setPadding(20, 15, 20, 15);
        shareButton.setBackground(context.getDrawable(android.R.drawable.btn_default));

        shareButton.setOnClickListener(v -> shareAnalysis(context, analysis));

        container.addView(shareButton, createMarginParams(0, 20, 0, 0));
    }

    private static void shareAnalysis(Context context, FastAccurateGpxEvaluator.FastAccurateRouteAnalysis analysis) {
        StringBuilder shareText = new StringBuilder();
        shareText.append("GPX Route Analysis Results (Fast & Accurate Method)\n");
        shareText.append("===================================================\n\n");
        shareText.append(String.format("Bike Type: %s %s\n",
                analysis.analyzedForBikeType.getEmoji(),
                analysis.analyzedForBikeType.getDisplayName()));
        shareText.append(String.format("Total Distance: %.2f km\n", analysis.totalDistance));
        shareText.append(String.format("Data Coverage: %.1f%%\n\n", analysis.dataCoveragePercentage));

        shareText.append("Road Quality Breakdown:\n");
        shareText.append(String.format("🟢 Excellent (≥20): %.2f km (%.1f%%)\n",
                analysis.greenDistance, analysis.greenPercentage));
        shareText.append(String.format("🟡 Decent (10-19): %.2f km (%.1f%%)\n",
                analysis.yellowDistance, analysis.yellowPercentage));
        shareText.append(String.format("🔴 Poor (<10): %.2f km (%.1f%%)\n",
                analysis.redDistance, analysis.redPercentage));
        shareText.append(String.format("⚪ No Match: %.2f km (%.1f%%)\n\n",
                analysis.unknownDistance, analysis.unknownPercentage));

        shareText.append(String.format("Assessment: %s\n", analysis.getQualityAssessment()));
        shareText.append(String.format("Elevation: %s\n\n", analysis.getElevationAssessment()));

        shareText.append("Analysis Performance:\n");
        shareText.append(String.format("Roads Found: %d total\n", analysis.totalRoadsInArea));
        shareText.append(String.format("Roads Matched: %d used\n", analysis.totalRoadsMatched));
        shareText.append("Method: Fast & Accurate hybrid approach\n\n");

        shareText.append("Generated by GRVL Finder (Fast & Accurate Analysis)");

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "GPX Route Analysis (Fast & Accurate)");

        context.startActivity(Intent.createChooser(shareIntent, "Share Route Analysis"));
    }

    // Helper methods
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

    private static String formatSurfaceName(String surface) {
        switch (surface.toLowerCase()) {
            case "excellent_roads": return "🟢 Excellent Roads";
            case "decent_roads": return "🟡 Decent Roads";
            case "poor_roads": return "🔴 Poor Roads";
            case "no_road_match": return "⚪ No Road Match";
            case "asphalt": return "🛣️ Asphalt";
            case "gravel": return "🏞️ Gravel";
            case "fine_gravel": return "🏞️ Fine Gravel";
            case "paved": return "🛣️ Paved";
            case "concrete": return "🛣️ Concrete";
            case "dirt": return "🌍 Dirt/Earth";
            case "unpaved": return "🌿 Unpaved";
            case "memory_error": return "⚠️ Memory Error";
            case "processing_error": return "❌ Processing Error";
            default:
                String formatted = surface.substring(0, 1).toUpperCase() + surface.substring(1).replace("_", " ");
                return "• " + formatted;
        }
    }

    private static int getCoverageColor(double coverage) {
        if (coverage >= 80) return Color.parseColor("#4CAF50");
        else if (coverage >= 60) return Color.parseColor("#8BC34A");
        else if (coverage >= 40) return Color.parseColor("#FF9800");
        else return Color.parseColor("#F44336");
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