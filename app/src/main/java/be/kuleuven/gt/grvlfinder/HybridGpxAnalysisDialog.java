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
 * Dialog for displaying hybrid GPX analysis results using the clean GpxAnalysisDialog style
 * but showing the green/yellow/red classification data from HybridGpxEvaluator
 */
public class HybridGpxAnalysisDialog {

    public static void show(Context context, HybridGpxEvaluator.HybridRouteAnalysis analysis) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        ScrollView scroll = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(40, 40, 40, 40);

        // Title
        TextView title = new TextView(context);
        title.setText("GPX Route Analysis (Hybrid Method)");
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

        // Road quality breakdown section (main improvement)
        TextView qualityTitle = new TextView(context);
        qualityTitle.setText("Road Quality Analysis (Find Gravel Method)");
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

        // Bike type compatibility section
        addBikeTypeCompatibilitySection(container, analysis);

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

        // Analysis method and coverage information
        addAnalysisMethodSection(container, analysis);

        // Detailed surface breakdown (if available)
        if (!analysis.surfaceBreakdown.isEmpty()) {
            addDetailedSurfaceSection(container, analysis);
        }

        // Route suitability summary
        addSuitabilitySummary(container, analysis);

        // Share button
        addShareButton(container, context, analysis);

        scroll.addView(container);
        dialog.setContentView(scroll);
        dialog.show();
    }

    private static void addBikeTypeCompatibilitySection(LinearLayout container, HybridGpxEvaluator.HybridRouteAnalysis analysis) {
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

            if (analysis.greenPercentage < 50) {
                TextView roadWarning = new TextView(container.getContext());
                roadWarning.setText("⚠️ Limited high-quality paved roads for road cycling");
                roadWarning.setTextColor(Color.parseColor("#F57C00"));
                roadWarning.setTextSize(12f);
                container.addView(roadWarning, createMarginParams(10, 5, 10, 10));
            }

        } else if (bikeType.name().contains("GRAVEL")) {
            // For gravel bikes, show combined good roads
            double goodRoads = analysis.greenPercentage + analysis.yellowPercentage;
            addMetricView(container, "Suitable Roads (Green + Yellow)",
                    String.format(Locale.US, "%.1f%%", goodRoads),
                    goodRoads >= 70 ? Color.parseColor("#4CAF50") :
                            goodRoads >= 50 ? Color.parseColor("#FF9800") : Color.parseColor("#F44336"));

            if (goodRoads >= 70) {
                TextView gravelNote = new TextView(container.getContext());
                gravelNote.setText("✅ Excellent route for gravel biking");
                gravelNote.setTextColor(Color.parseColor("#4CAF50"));
                gravelNote.setTextSize(12f);
                container.addView(gravelNote, createMarginParams(10, 5, 10, 10));
            }
        }
    }

    private static void addAnalysisMethodSection(LinearLayout container, HybridGpxEvaluator.HybridRouteAnalysis analysis) {
        TextView methodTitle = new TextView(container.getContext());
        methodTitle.setText("Analysis Coverage");
        methodTitle.setTextSize(16f);
        methodTitle.setTypeface(Typeface.DEFAULT_BOLD);
        methodTitle.setTextColor(Color.parseColor("#424242"));
        container.addView(methodTitle, createMarginParams(0, 20, 0, 10));

        double coverage = analysis.getDataCoveragePercentage();
        String coverageText = String.format(Locale.US, "%.1f%% (%d/%d segments)",
                coverage, analysis.segmentsWithRoadData, analysis.totalSegmentsAnalyzed);

        int coverageColor;
        if (coverage >= 70) coverageColor = Color.parseColor("#4CAF50");
        else if (coverage >= 40) coverageColor = Color.parseColor("#FF9800");
        else coverageColor = Color.parseColor("#F44336");

        addMetricView(container, "Road Data Coverage", coverageText, coverageColor);

        // Roads found in area
        addMetricView(container, "Total Roads Found",
                String.valueOf(analysis.totalRoadsInArea),
                Color.parseColor("#1976D2"));

        // Method description
        TextView methodDescription = new TextView(container.getContext());
        methodDescription.setText("Method: Hybrid analysis combining speed optimizations with Find Gravel accuracy. " +
                "Routes are matched to OpenStreetMap roads and scored using the same system as the main app.");
        methodDescription.setTextSize(12f);
        methodDescription.setTextColor(Color.parseColor("#757575"));
        methodDescription.setLineSpacing(4, 1.2f);
        container.addView(methodDescription, createMarginParams(0, 10, 0, 5));

        if (coverage < 50) {
            TextView coverageNote = new TextView(container.getContext());
            coverageNote.setText("ℹ️ Low data coverage - route may use paths not in OpenStreetMap or areas between known roads.");
            coverageNote.setTextSize(12f);
            coverageNote.setTextColor(Color.parseColor("#757575"));
            container.addView(coverageNote, createMarginParams(10, 10, 10, 5));
        }
    }

    private static void addDetailedSurfaceSection(LinearLayout container, HybridGpxEvaluator.HybridRouteAnalysis analysis) {
        TextView detailTitle = new TextView(container.getContext());
        detailTitle.setText("Surface Type Breakdown");
        detailTitle.setTextSize(16f);
        detailTitle.setTypeface(Typeface.DEFAULT_BOLD);
        detailTitle.setTextColor(Color.parseColor("#424242"));
        container.addView(detailTitle, createMarginParams(0, 20, 0, 10));

        // Show major surface categories first
        for (Map.Entry<String, Double> entry : analysis.surfaceBreakdown.entrySet()) {
            if (entry.getValue() > 0.1) { // Only show surfaces with > 100m
                String surfaceName = formatSurfaceName(entry.getKey());
                String distanceText = String.format(Locale.US, "%.2f km", entry.getValue());

                // Color code by surface type
                int color = getSurfaceColor(entry.getKey());
                addDetailView(container, surfaceName, distanceText, color);
            }
        }
    }

    private static void addSuitabilitySummary(LinearLayout container, HybridGpxEvaluator.HybridRouteAnalysis analysis) {
        TextView summaryTitle = new TextView(container.getContext());
        summaryTitle.setText("Route Suitability");
        summaryTitle.setTextSize(16f);
        summaryTitle.setTypeface(Typeface.DEFAULT_BOLD);
        summaryTitle.setTextColor(Color.parseColor("#424242"));
        container.addView(summaryTitle, createMarginParams(0, 25, 0, 10));

        TextView summaryText = new TextView(container.getContext());
        String summary = generateHybridSuitabilitySummary(analysis);
        summaryText.setText(summary);
        summaryText.setTextSize(13f);
        summaryText.setTextColor(Color.parseColor("#424242"));
        summaryText.setLineSpacing(4, 1.2f);
        container.addView(summaryText, createMarginParams(0, 0, 0, 10));
    }

    private static String generateHybridSuitabilitySummary(HybridGpxEvaluator.HybridRouteAnalysis analysis) {
        StringBuilder summary = new StringBuilder();

        BikeType bikeType = analysis.analyzedForBikeType;

        switch (bikeType) {
            case RACE_ROAD:
                if (analysis.greenPercentage > 70) {
                    summary.append("✅ Excellent for road cycling - predominantly high-quality roads.");
                } else if (analysis.greenPercentage > 50) {
                    summary.append("✅ Good for road cycling with some challenging sections.");
                } else if (analysis.greenPercentage > 30) {
                    summary.append("⚠️ Mixed suitability for road cycling - significant poor quality sections.");
                } else {
                    summary.append("❌ Not recommended for road cycling - limited quality road surfaces.");
                }
                break;

            case GRAVEL_BIKE:
                double gravelSuitable = analysis.greenPercentage + analysis.yellowPercentage;
                if (gravelSuitable > 80) {
                    summary.append("✅ Outstanding for gravel biking - excellent surface variety.");
                } else if (gravelSuitable > 60) {
                    summary.append("✅ Very good for gravel biking with good surface mix.");
                } else if (gravelSuitable > 40) {
                    summary.append("✅ Suitable for gravel biking with some challenging areas.");
                } else {
                    summary.append("⚠️ Limited gravel suitability - many poor quality sections.");
                }
                break;

            case RACE_BIKEPACKING:
                summary.append("🚴‍♂️ Road touring analysis: ");
                if (analysis.greenPercentage > 60 && analysis.maxSlope < 10.0) {
                    summary.append("✅ Excellent for loaded road touring.");
                } else if (analysis.maxSlope > 15.0) {
                    summary.append("❌ Very steep sections dangerous with loaded bike.");
                } else if (analysis.maxSlope > 12.0) {
                    summary.append("⚠️ Steep sections challenging with touring load.");
                } else {
                    summary.append("✅ Good for road touring with proper gear ratios.");
                }
                break;

            case GRAVEL_BIKEPACKING:
                summary.append("🎒 Adventure touring analysis: ");
                double adventureSuitable = analysis.greenPercentage + analysis.yellowPercentage;
                if (adventureSuitable > 70 && analysis.maxSlope < 12.0) {
                    summary.append("✅ Excellent adventure route with manageable terrain.");
                } else if (analysis.maxSlope > 15.0) {
                    summary.append("❌ Extreme terrain - only for very experienced bikepackers.");
                } else if (analysis.redPercentage > 40) {
                    summary.append("⚠️ Challenging route - prepare for difficult conditions.");
                } else {
                    summary.append("✅ Good adventure route - expect varied conditions.");
                }
                break;

            case CUSTOM:
                summary.append("⚙️ Custom analysis: ");
                if (analysis.greenPercentage > 60) {
                    summary.append("✅ Route matches your custom criteria well.");
                } else if (analysis.greenPercentage + analysis.yellowPercentage > 60) {
                    summary.append("✅ Route partially matches your custom criteria.");
                } else {
                    summary.append("⚠️ Route has limited match to your custom criteria.");
                }
                break;
        }

        // Add data quality note
        if (analysis.getDataCoveragePercentage() < 50) {
            summary.append("\n\n📊 Note: Analysis based on limited road data coverage (")
                    .append(String.format("%.0f", analysis.getDataCoveragePercentage()))
                    .append("%). Actual conditions may vary.");
        } else if (analysis.totalRoadsInArea > 100) {
            summary.append("\n\n📊 High-quality analysis based on comprehensive road data (")
                    .append(analysis.totalRoadsInArea).append(" roads analyzed).");
        }

        return summary.toString();
    }

    private static void addShareButton(LinearLayout container, Context context, HybridGpxEvaluator.HybridRouteAnalysis analysis) {
        TextView shareButton = new TextView(context);
        shareButton.setText("📤 Share Analysis");
        shareButton.setTextSize(16f);
        shareButton.setTextColor(Color.parseColor("#1976D2"));
        shareButton.setTypeface(Typeface.DEFAULT_BOLD);
        shareButton.setPadding(20, 15, 20, 15);
        shareButton.setBackground(context.getDrawable(android.R.drawable.btn_default));

        shareButton.setOnClickListener(v -> shareHybridAnalysis(context, analysis));

        container.addView(shareButton, createMarginParams(0, 20, 0, 0));
    }

    private static void shareHybridAnalysis(Context context, HybridGpxEvaluator.HybridRouteAnalysis analysis) {
        StringBuilder shareText = new StringBuilder();
        shareText.append("GPX Route Analysis Results (Hybrid Method)\n");
        shareText.append("========================================\n\n");
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

        shareText.append("Analysis Method: Hybrid approach combining speed with Find Gravel accuracy\n");
        shareText.append(String.format("Roads Analyzed: %d total\n\n", analysis.totalRoadsInArea));

        shareText.append("Generated by GRVL Finder (Hybrid Analysis)");

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "GPX Route Analysis (Hybrid Method)");

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

    private static void addDetailView(LinearLayout container, String label, String value, int color) {
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
        valueView.setTextColor(color);
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
            case "no_road_data": return "⚪ No Road Data";
            case "asphalt": return "🛣️ Asphalt";
            case "gravel": return "🏞️ Gravel";
            case "fine_gravel": return "🏞️ Fine Gravel";
            case "paved": return "🛣️ Paved";
            case "concrete": return "🛣️ Concrete";
            case "dirt": return "🌍 Dirt/Earth";
            case "unpaved": return "🌿 Unpaved";
            case "processing_error": return "❌ Processing Error";
            default:
                String formatted = surface.substring(0, 1).toUpperCase() + surface.substring(1).replace("_", " ");
                return "• " + formatted;
        }
    }

    private static int getSurfaceColor(String surface) {
        switch (surface.toLowerCase()) {
            case "excellent_roads": return Color.parseColor("#4CAF50");
            case "decent_roads": return Color.parseColor("#FF9800");
            case "poor_roads": return Color.parseColor("#F44336");
            case "no_road_data": return Color.parseColor("#9E9E9E");
            case "asphalt": case "paved": case "concrete": return Color.parseColor("#1976D2");
            case "gravel": case "fine_gravel": return Color.parseColor("#8BC34A");
            case "dirt": case "unpaved": return Color.parseColor("#795548");
            case "processing_error": return Color.parseColor("#F44336");
            default: return Color.parseColor("#424242");
        }
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