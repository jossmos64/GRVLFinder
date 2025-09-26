package be.kuleuven.gt.grvlfinder;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.util.Locale;

/**
 * Dialog to display results from PolylineBasedGpxEvaluator using the same approach as "Find Gravel"
 */
public class PolylineGpxAnalysisDialog {

    public static void show(Context context, PolylineBasedGpxEvaluator.PolylineRouteAnalysis analysis) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_enhanced_gpx_analysis, null);

        setupViews(dialogView, analysis);

        builder.setView(dialogView);
        builder.setTitle("Route Analysis (Find Gravel Method) - " + analysis.analyzedForBikeType.getEmoji() + " " +
                analysis.analyzedForBikeType.getDisplayName());

        AlertDialog dialog = builder.create();

        // Setup buttons
        Button closeButton = dialogView.findViewById(R.id.closeButton);
        Button shareButton = dialogView.findViewById(R.id.shareAnalysisButton);

        closeButton.setOnClickListener(v -> dialog.dismiss());
        shareButton.setOnClickListener(v -> shareAnalysis(context, analysis));

        dialog.show();
    }

    private static void setupViews(View dialogView, PolylineBasedGpxEvaluator.PolylineRouteAnalysis analysis) {
        // Basic route info - now includes roads found in area
        TextView routeInfoText = dialogView.findViewById(R.id.routeInfoText);
        String routeInfo = String.format(Locale.US,
                "Total Distance: %.2f km\n" +
                        "Segments Analyzed: %d\n" +
                        "Data Coverage: %.1f%%\n" +
                        "Roads Found in Area: %d",
                analysis.totalDistance,
                analysis.totalSegmentsAnalyzed,
                analysis.dataCoveragePercentage,
                analysis.totalRoadsInArea);
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
                "Excellent Roads (Score ≥ 20)",
                analysis.greenDistance,
                analysis.greenPercentage,
                Color.parseColor("#228B22"));

        // Road quality breakdown - Yellow category
        setupQualityCategory(dialogView, R.id.yellowCategoryContainer,
                "Decent Roads (Score 10-19)",
                analysis.yellowDistance,
                analysis.yellowPercentage,
                Color.parseColor("#FFA500"));

        // Road quality breakdown - Red category
        setupQualityCategory(dialogView, R.id.redCategoryContainer,
                "Poor Roads (Score < 10)",
                analysis.redDistance,
                analysis.redPercentage,
                Color.parseColor("#DC143C"));

        // Unknown data category
        setupQualityCategory(dialogView, R.id.unknownCategoryContainer,
                "No Road Match Found",
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

        // Analysis method info (specific to polyline approach)
        setupAnalysisMethodInfo(dialogView, analysis);

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

    private static void setupAnalysisMethodInfo(View dialogView, PolylineBasedGpxEvaluator.PolylineRouteAnalysis analysis) {
        // Add a section explaining the polyline-based approach
        LinearLayout surfaceContainer = dialogView.findViewById(R.id.surfaceBreakdownContainer);
        if (surfaceContainer != null) {
            // Clear any existing content
            surfaceContainer.removeAllViews();

            TextView methodTitle = new TextView(dialogView.getContext());
            methodTitle.setText("Analysis Method");
            methodTitle.setTextSize(16);
            methodTitle.setTextColor(Color.parseColor("#424242"));
            methodTitle.setPadding(0, 16, 0, 8);
            surfaceContainer.addView(methodTitle);

            TextView methodDescription = new TextView(dialogView.getContext());
            String description = String.format(Locale.US,
                    "This analysis uses the same approach as \"Find Gravel\":\n\n" +
                            "1. Found %d roads in the route area using OpenStreetMap\n" +
                            "2. Applied the same scoring system as the main app\n" +
                            "3. Matched your route segments to these existing roads\n" +
                            "4. Classified roads by quality: Green (≥20), Yellow (10-19), Red (<10)\n\n" +
                            "Coverage: %.1f%% of your route matched to known roads",
                    analysis.totalRoadsInArea,
                    analysis.dataCoveragePercentage);

            methodDescription.setText(description);
            methodDescription.setTextSize(12);
            methodDescription.setTextColor(Color.parseColor("#666666"));
            methodDescription.setLineSpacing(4, 1.2f);
            surfaceContainer.addView(methodDescription);
        }

        // Update the toggle to show method info instead of surface breakdown
        TextView surfaceToggle = dialogView.findViewById(R.id.surfaceBreakdownToggle);
        if (surfaceToggle != null) {
            surfaceToggle.setText("▶ Analysis Method Details (tap to show)");
            surfaceToggle.setOnClickListener(v -> {
                if (surfaceContainer != null) {
                    if (surfaceContainer.getVisibility() == View.GONE) {
                        surfaceContainer.setVisibility(View.VISIBLE);
                        surfaceToggle.setText("▼ Analysis Method Details (tap to hide)");
                    } else {
                        surfaceContainer.setVisibility(View.GONE);
                        surfaceToggle.setText("▶ Analysis Method Details (tap to show)");
                    }
                }
            });
        }
    }

    private static void setupRecommendations(View dialogView, PolylineBasedGpxEvaluator.PolylineRouteAnalysis analysis) {
        TextView recommendationsText = dialogView.findViewById(R.id.recommendationsText);
        if (recommendationsText == null) return;

        StringBuilder recommendations = new StringBuilder();
        recommendations.append("Recommendations:\n\n");

        BikeType bikeType = analysis.analyzedForBikeType;

        // Polyline-specific recommendations
        if (analysis.totalRoadsInArea == 0) {
            recommendations.append("• No roads found in route area. Route may use paths not in OpenStreetMap.\n");
        } else if (analysis.dataCoveragePercentage < 50) {
            recommendations.append("• Low route matching (").append(String.format("%.1f", analysis.dataCoveragePercentage))
                    .append("%). Route may use paths between known roads.\n");
        } else if (analysis.dataCoveragePercentage >= 80) {
            recommendations.append("• Excellent route matching (").append(String.format("%.1f", analysis.dataCoveragePercentage))
                    .append("%). Analysis is highly reliable.\n");
        }

        // Bike type specific recommendations (same as enhanced version)
        if (bikeType == BikeType.RACE_ROAD) {
            if (analysis.greenPercentage < 50) {
                recommendations.append("• Limited high-quality paved roads. Consider an alternative route or switch to gravel bike.\n");
            }
            if (analysis.redPercentage > 30) {
                recommendations.append("• Many poor quality segments detected. Road bike may struggle.\n");
            }
            if (analysis.maxSlope >= 12.0) {
                recommendations.append("• Steep climbs present. Consider gearing and fitness level.\n");
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
                recommendations.append("• Very steep sections detected. Consider load distribution and gearing.\n");
            }
            if (analysis.redPercentage > 35) {
                recommendations.append("• Many challenging segments. Plan for slower progress with loaded bike.\n");
            }
        }

        // Data quality recommendations
        if (analysis.totalRoadsInArea > 100) {
            recommendations.append("• Rich road data available (").append(analysis.totalRoadsInArea)
                    .append(" roads found). Analysis is comprehensive.\n");
        } else if (analysis.totalRoadsInArea < 20) {
            recommendations.append("• Limited road data in area (").append(analysis.totalRoadsInArea)
                    .append(" roads). Consider checking route on main map.\n");
        }

        // Positive recommendations
        if (analysis.greenPercentage >= 70) {
            recommendations.append("• Outstanding route quality! Perfect for your selected bike type.\n");
        }

        if (recommendations.length() <= 20) { // Just "Recommendations:\n\n"
            recommendations.append("• Route analysis complete using Find Gravel method. Check details above.");
        }

        recommendationsText.setText(recommendations.toString());
    }

    private static void shareAnalysis(Context context, PolylineBasedGpxEvaluator.PolylineRouteAnalysis analysis) {
        StringBuilder shareText = new StringBuilder();
        shareText.append("GPX Route Analysis Results (Find Gravel Method)\n");
        shareText.append("==============================================\n\n");
        shareText.append(String.format("Bike Type: %s %s\n",
                analysis.analyzedForBikeType.getEmoji(),
                analysis.analyzedForBikeType.getDisplayName()));
        shareText.append(String.format("Total Distance: %.2f km\n", analysis.totalDistance));
        shareText.append(String.format("Data Coverage: %.1f%%\n", analysis.dataCoveragePercentage));
        shareText.append(String.format("Roads Found in Area: %d\n\n", analysis.totalRoadsInArea));

        shareText.append("Road Quality Breakdown (Using Find Gravel Scoring):\n");
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

        shareText.append("Analysis Method: Same approach as Find Gravel - matched route to ")
                .append(analysis.totalRoadsInArea).append(" roads found in area.\n\n");

        shareText.append("Generated by GRVL Finder (Polyline Analysis)");

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "GPX Route Analysis (Find Gravel Method)");

        context.startActivity(Intent.createChooser(shareIntent, "Share Route Analysis"));
    }
}