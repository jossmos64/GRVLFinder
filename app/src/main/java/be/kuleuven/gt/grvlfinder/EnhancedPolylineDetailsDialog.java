package be.kuleuven.gt.grvlfinder;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.Map;

public class EnhancedPolylineDetailsDialog {

    public static void show(Context context, PolylineResult polylineResult,
                            WeatherAwareScoreCalculator weatherCalc) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        ScrollView scroll = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(40, 40, 40, 40);

        // Title
        TextView title = new TextView(context);
        title.setText("Road Details / Wegdetails");
        title.setTextSize(18f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        container.addView(title);

        // Weather Warning (if applicable)
        if (weatherCalc != null && weatherCalc.hasWeatherData()) {
            String weatherWarning = weatherCalc.getWeatherWarning(polylineResult.getTags());
            if (weatherWarning != null) {
                TextView warningView = createWarningView(context, weatherWarning);
                container.addView(warningView);

                // Add spacing
                View spacer = new View(context);
                LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 20);
                spacer.setLayoutParams(spacerParams);
                container.addView(spacer);
            }
        }

        // Score
        int score = polylineResult.getScore();
        TextView scoreView = new TextView(context);
        scoreView.setText("Score: " + score);
        scoreView.setTextSize(16f);
        scoreView.setTypeface(Typeface.DEFAULT_BOLD);
        scoreView.setPadding(0, 10, 0, 10);

        // Color code score
        if (score >= 20) {
            scoreView.setTextColor(Color.parseColor("#228B22")); // Green
        } else if (score >= 10) {
            scoreView.setTextColor(Color.parseColor("#FFA500")); // Orange
        } else {
            scoreView.setTextColor(Color.parseColor("#DC143C")); // Red
        }

        container.addView(scoreView);

        // Slope information
        double maxSlope = polylineResult.getMaxSlopePercent();
        TextView slopeView = new TextView(context);

        if (maxSlope >= 0) {
            slopeView.setText(String.format("Max. helling / slope: %.1f%%", maxSlope));

            // Color code based on slope severity
            if (maxSlope > 12.0) {
                slopeView.setTextColor(Color.RED);
                slopeView.setTypeface(Typeface.DEFAULT_BOLD);
            } else if (maxSlope > 10.0) {
                slopeView.setTextColor(Color.RED);
            } else if (maxSlope > 8.0) {
                slopeView.setTextColor(Color.parseColor("#FF6600")); // Dark orange
            } else if (maxSlope > 6.0) {
                slopeView.setTextColor(Color.parseColor("#FFA500")); // Orange
            } else if (maxSlope > 4.0) {
                slopeView.setTextColor(Color.parseColor("#FFD700")); // Gold
            } else {
                slopeView.setTextColor(Color.parseColor("#228B22")); // Green
            }
        } else {
            slopeView.setText("Max. helling: onbekend (geen hoogte data)");
            slopeView.setTextColor(Color.GRAY);
        }

        slopeView.setTextSize(14f);
        slopeView.setPadding(0, 0, 0, 10);
        container.addView(slopeView);

        // Add slope warnings
        if (maxSlope > 12.0) {
            TextView slopeWarning = new TextView(context);
            slopeWarning.setText("⚠️ Zeer steile helling - mogelijk moeilijk berijdbaar");
            slopeWarning.setTextColor(Color.RED);
            slopeWarning.setTextSize(12f);
            slopeWarning.setPadding(0, 0, 0, 15);
            container.addView(slopeWarning);
        } else if (maxSlope > 8.0) {
            TextView slopeWarning = new TextView(context);
            slopeWarning.setText("⚠️ Steile helling - uitdagend");
            slopeWarning.setTextColor(Color.parseColor("#FF6600"));
            slopeWarning.setTextSize(12f);
            slopeWarning.setPadding(0, 0, 0, 15);
            container.addView(slopeWarning);
        }

        // Weather impact explanation (if muddy conditions)
        if (weatherCalc != null && weatherCalc.hasWeatherData() &&
                weatherCalc.getCurrentWeatherCondition().isMuddy) {

            String surface = polylineResult.getTags().get("surface");
            String impactExplanation = getWeatherImpactExplanation(surface);

            if (impactExplanation != null) {
                TextView impactText = new TextView(context);
                impactText.setText(impactExplanation);
                impactText.setTextSize(12f);
                impactText.setTextColor(Color.parseColor("#666666"));
                impactText.setPadding(0, 5, 0, 15);
                container.addView(impactText);
            }
        }

        // OSM Tags
        Map<String, String> tags = polylineResult.getTags();
        if (tags != null && !tags.isEmpty()) {
            TextView tagsTitle = new TextView(context);
            tagsTitle.setText("OSM Tags:");
            tagsTitle.setTextSize(14f);
            tagsTitle.setTypeface(Typeface.DEFAULT_BOLD);
            tagsTitle.setPadding(0, 10, 0, 5);
            container.addView(tagsTitle);

            for (Map.Entry<String, String> entry : tags.entrySet()) {
                TextView tagView = new TextView(context);
                tagView.setText(entry.getKey() + " = " + entry.getValue());
                tagView.setTextSize(13f);
                tagView.setPadding(10, 2, 0, 2);
                container.addView(tagView);
            }
        } else {
            TextView noTags = new TextView(context);
            noTags.setText("Geen OSM tags beschikbaar");
            noTags.setTextSize(13f);
            noTags.setTextColor(Color.GRAY);
            noTags.setPadding(0, 10, 0, 0);
            container.addView(noTags);
        }

        scroll.addView(container);
        dialog.setContentView(scroll);

        // Add weather details button if data available
        if (weatherCalc != null && weatherCalc.hasWeatherData()) {
            dialog.setOnShowListener(dialogInterface -> {
            });
        }

        dialog.show();
    }

    private static TextView createWarningView(Context context, String warning) {
        TextView warningView = new TextView(context);
        warningView.setText(warning);
        warningView.setTextSize(14f);
        warningView.setTextColor(Color.parseColor("#FF6600")); // Orange
        warningView.setTypeface(Typeface.DEFAULT_BOLD);
        warningView.setPadding(20, 20, 20, 20);
        warningView.setBackgroundColor(Color.parseColor("#22FF6600")); // Light orange background

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 10, 0, 0);
        warningView.setLayoutParams(params);

        return warningView;
    }

    private static String getWeatherImpactExplanation(String surface) {
        if (surface == null) {
            return "Recent rain may affect unpaved road conditions.";
        }

        surface = surface.toLowerCase();

        if (surface.contains("dirt") || surface.contains("ground") ||
                surface.contains("earth") || surface.contains("unpaved")) {
            return "💧 This dirt road is likely to be muddy and difficult to ride after recent rain.";
        }

        if (surface.contains("gravel") || surface.contains("compacted")) {
            return "💧 This gravel road may have muddy sections after recent rain.";
        }

        // Paved roads - no explanation needed
        return null;
    }
}