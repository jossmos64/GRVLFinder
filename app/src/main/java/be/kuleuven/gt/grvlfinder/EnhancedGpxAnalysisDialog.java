package be.kuleuven.gt.grvlfinder;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.util.Locale;
import java.util.Map;

/**
 * Enhanced dialog to display GPX analysis results using the same green/yellow/red system as main app
 */
public class EnhancedGpxAnalysisDialog {

    public static void show(Context context, ImprovedGpxEvaluator.EnhancedRouteAnalysis analysis) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_enhanced_gpx_analysis, null);

        setupViews(dialogView, analysis);

        builder.setView(dialogView);
        builder.setTitle("Route Analysis - " + analysis.analyzedForBikeType.getEmoji() + " " +
                analysis.analyzedForBikeType.getDisplayName());

        AlertDialog dialog = builder.create();

        // Setup buttons
        Button closeButton = dialogView.findViewById(R.id.closeButton);
        Button shareButton = dialogView.findViewById(R.id.shareAnalysisButton);

        closeButton.setOnClickListener(v -> dialog.dismiss());
        shareButton.setOnClickListener(v -> shareAnalysis(context, analysis));

        dialog.show();
    }

    private static void setupViews(View dialogView, ImprovedGpxEvaluator.EnhancedRouteAnalysis analysis) {
        // Basic route info
        TextView routeInfoText = dialogView.findViewById(R.id.routeInfoText);
        String routeInfo = String.format(Locale.US,
                "Total Distance: %.2f km\n" +
                        "Segments Analyzed: %d\n" +
                        "Data Coverage: %.1f%%",
                analysis.totalDistance,
                analysis.totalSegmentsAnalyzed,
                analysis.dataCoveragePercentage);
        routeInfoText.setText(routeInfo);

        // Quality assessment
        TextView qualityAssessmentText = dialogView.findViewById(R.id.qualityAssessmentText);
        qualityAssessmentText.setText(analysis.getQualityAssessment());

        // Set assessment text color based on overall quality
        if (analysis.greenPercentage >= 60) {
            qualityAssessmentText.setTextColor(Color.parseColor("#228B22")); // Green
        } else if (analysis.greenPercentage + analysis.yellowPercentage >= 70) {
            qualityAssessmentText.setTextColor(Color.parseColor("#FF8C00")); // Orange
        } else {
            qualityAssessmentText.setTextColor(Color.parseColor("#DC143C")); // Red
        }

        // Road quality breakdown - Green category
        setupQualityCategory(dialogView, R.id.greenCategoryContainer,
                "Excellent Roads",
                analysis.greenDistance,
                analysis.greenPercentage,
                Color.parseColor("#228B22"));

        // Road quality breakdown - Yellow category
        setupQualityCategory(dialogView, R.id.yellowCategoryContainer,
                "Decent Roads",
                analysis.yellowDistance,
                analysis.yellowPercentage,
                Color.parseColor("#FFA500"));

        // Road quality breakdown - Red category
        setupQualityCategory(dialogView, R.id.redCategoryContainer,
                "Poor Roads",
                analysis.redDistance,
                analysis.redPercentage,
                Color.parseColor("#DC143C"));

        // Unknown data category
        setupQualityCategory(dialogView, R.id.unknownCategoryContainer,
                "No Road Data",
                analysis.unknownDistance,
                analysis.unknownPercentage,
                Color.parseColor("#808080"));

        // Elevation information
        TextView elevationInfoText = dialogView.findViewById(R.id.elevationInfoText);
        String elevationInfo = analysis.getElevationAssessment();
        if (analysis.hasElevationData && analysis.maxSlope >= 0) {
            elevationInfo += String.format(Locale.US, "\nSteepest point: %s",
                    analysis.steepestLocationDescription);
        }
        elevationInfoText.setText(elevationInfo);

        // Set elevation text color based on steepness
        if (analysis.maxSlope >= 15.0) {
            elevationInfoText.setTextColor(Color.parseColor("#DC143C")); // Red for very steep
        } else if (analysis.maxSlope >= 12.0) {
            elevationInfoText.setTextColor(Color.parseColor("#FFA500")); // Orange for steep
        } else {
            elevationInfoText.setTextColor(Color.parseColor("#228B22")); // Green for moderate
        }

        // Surface breakdown details (expandable section)
        setupSurfaceBreakdown(dialogView, analysis);

        // Recommendations based on bike type and analysis
        setupRecommendations(dialogView, analysis);
    }

    private static void setupQualityCategory(View dialogView, int containerId, String categoryName,
                                             double distance, double percentage, int color) {
        LinearLayout container = dialogView.findViewById(containerId);
        if (container == null) return;

        // Create category header
        TextView categoryHeader = new TextView(dialogView.getContext());
        categoryHeader.setText(categoryName);
        categoryHeader.setTextSize(16);
        categoryHeader.setTextColor(color);
        categoryHeader.setPadding(0, 8, 0, 4);
        container.addView(categoryHeader);

        // Create progress bar
        ProgressBar progressBar = new ProgressBar(dialogView.getContext(), null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress((int) Math.round(percentage));

        // Apply custom styling
        progressBar.getProgressDrawable().setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);

        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 24);
        progressParams.setMargins(0, 4, 0, 4);
        progressBar.setLayoutParams(progressParams);
        container.addView(progressBar);

        // Create distance and percentage text
        TextView detailText = new TextView(dialogView.getContext());
        String detailInfo = String.format(Locale.US, "%.2f km (%.1f%%)", distance, percentage);
        detailText.setText(detailInfo);
        detailText.setTextSize(14);
        detailText.setTextColor(Color.parseColor("#666666"));
        detailText.setPadding(0, 0, 0, 8);
        container.addView(detailText);
    }

    private static void setupSurfaceBreakdown(View dialogView, ImprovedGpxEvaluator.EnhancedRouteAnalysis analysis) {
        LinearLayout surfaceContainer = dialogView.findViewById(R.id.surfaceBreakdownContainer);
        TextView surfaceToggle = dialogView.findViewById(R.id.surfaceBreakdownToggle);

        if (surfaceContainer == null || surfaceToggle == null) return;

        // Initially hide surface breakdown
        surfaceContainer.setVisibility(View.GONE);

        surfaceToggle.setOnClickListener(v -> {
            if (surfaceContainer.getVisibility() == View.GONE) {
                surfaceContainer.setVisibility(View.VISIBLE);
                surfaceToggle.setText("▼ Surface Details (tap to hide)");
                populateSurfaceBreakdown(surfaceContainer, analysis);
            } else {
                surfaceContainer.setVisibility(View.GONE);
                surfaceToggle.setText("▶ Surface Details (tap to show)");
            }
        });
    }

    private static void populateSurfaceBreakdown(LinearLayout container,
                                                 ImprovedGpxEvaluator.EnhancedRouteAnalysis analysis) {
        container.removeAllViews();

        for (Map.Entry<String, Double> entry : analysis.surfaceBreakdown.entrySet()) {
            String surface = entry.getKey();
            Double distance = entry.getValue();

            if (distance > 0.01) { // Only show surfaces with meaningful distance
                TextView surfaceItem = new TextView(container.getContext());
                String surfaceInfo = String.format(Locale.US, "• %s: %.2f km",
                        formatSurfaceName(surface), distance);
                surfaceItem.setText(surfaceInfo);
                surfaceItem.setTextSize(12);
                surfaceItem.setTextColor(Color.parseColor("#555555"));
                surfaceItem.setPadding(16, 2, 0, 2);
                container.addView(surfaceItem);
            }
        }
    }

    private static String formatSurfaceName(String surface) {
        switch (surface.toLowerCase()) {
            case "excellent_roads": return "Excellent Roads";
            case "decent_roads": return "Decent Roads";
            case "poor_roads": return "Poor Quality Roads";
            case "no_road_data": return "No Road Data Available";
            case "asphalt": return "Asphalt";
            case "gravel": return "Gravel";
            case "fine_gravel": return "Fine Gravel";
            case "paved": return "Paved";
            case "concrete": return "Concrete";
            case "dirt": return "Dirt/Earth";
            case "unpaved": return "Unpaved";
            default: return surface.substring(0, 1).toUpperCase() + surface.substring(1).replace("_", " ");
        }
    }

    private static void setupRecommendations(View dialogView, ImprovedGpxEvaluator.EnhancedRouteAnalysis analysis) {
        TextView recommendationsText = dialogView.findViewById(R.id.recommendationsText);
        if (recommendationsText == null) return;

        StringBuilder recommendations = new StringBuilder();
        recommendations.append("Recommendations:\n\n");

        BikeType bikeType = analysis.analyzedForBikeType;

        // Bike type specific recommendations
        if (bikeType == BikeType.RACE_ROAD) {
            if (analysis.greenPercentage < 50) {
                recommendations.append("• This route has limited high-quality paved roads. Consider an alternative route or switch to a gravel bike.\n");
            }
            if (analysis.redPercentage > 30) {
                recommendations.append("• Many poor quality segments detected. Road bike may struggle on this route.\n");
            }
            if (analysis.maxSlope >= 12.0) {
                recommendations.append("• Steep climbs present. Consider gearing and fitness level for road cycling.\n");
            }
        } else if (bikeType == BikeType.GRAVEL_BIKE) {
            if (analysis.greenPercentage + analysis.yellowPercentage >= 70) {
                recommendations.append("• Excellent route for gravel riding with good surface variety.\n");
            }
            if (analysis.redPercentage > 40) {
                recommendations.append("• Some challenging surfaces ahead. Check tire choice and bike setup.\n");
            }
        } else if (bikeType == BikeType.RACE_BIKEPACKING || bikeType == BikeType.GRAVEL_BIKEPACKING) {
            if (analysis.maxSlope >= 15.0) {
                recommendations.append("• Very steep sections detected. Consider load distribution and gearing for bikepacking.\n");
            }
            if (analysis.redPercentage > 35) {
                recommendations.append("• Many challenging segments. Plan for slower progress with loaded bike.\n");
            }
            if (analysis.dataCoveragePercentage < 60) {
                recommendations.append("• Limited road data available. Scout unknown sections or prepare for varied conditions.\n");
            }
        }

        // General recommendations based on data quality
        if (analysis.dataCoveragePercentage < 30) {
            recommendations.append("• Low data coverage. Actual conditions may vary significantly from this analysis.\n");
        }

        if (analysis.unknownPercentage > 50) {
            recommendations.append("• Many sections without road data. Consider alternate routing or additional research.\n");
        }

        // Positive recommendations
        if (analysis.greenPercentage >= 70) {
            recommendations.append("• Great route quality! Perfect for your selected bike type.\n");
        }

        if (recommendations.length() <= 20) { // Just "Recommendations:\n\n"
            recommendations.append("• Route analysis complete. Check the details above for specific information.");
        }

        recommendationsText.setText(recommendations.toString());
    }

    private static void shareAnalysis(Context context, ImprovedGpxEvaluator.EnhancedRouteAnalysis analysis) {
        StringBuilder shareText = new StringBuilder();
        shareText.append("GPX Route Analysis Results\n");
        shareText.append("========================\n\n");
        shareText.append(String.format("Bike Type: %s %s\n",
                analysis.analyzedForBikeType.getEmoji(),
                analysis.analyzedForBikeType.getDisplayName()));
        shareText.append(String.format("Total Distance: %.2f km\n", analysis.totalDistance));
        shareText.append(String.format("Data Coverage: %.1f%%\n\n", analysis.dataCoveragePercentage));

        shareText.append("Road Quality Breakdown:\n");
        shareText.append(String.format("🟢 Excellent: %.2f km (%.1f%%)\n",
                analysis.greenDistance, analysis.greenPercentage));
        shareText.append(String.format("🟡 Decent: %.2f km (%.1f%%)\n",
                analysis.yellowDistance, analysis.yellowPercentage));
        shareText.append(String.format("🔴 Poor: %.2f km (%.1f%%)\n",
                analysis.redDistance, analysis.redPercentage));
        shareText.append(String.format("⚪ Unknown: %.2f km (%.1f%%)\n\n",
                analysis.unknownDistance, analysis.unknownPercentage));

        shareText.append(String.format("Assessment: %s\n", analysis.getQualityAssessment()));
        shareText.append(String.format("Elevation: %s\n\n", analysis.getElevationAssessment()));

        shareText.append("Generated by GRVL Finder");

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "GPX Route Analysis");

        context.startActivity(Intent.createChooser(shareIntent, "Share Route Analysis"));
    }
}