package be.kuleuven.gt.grvlfinder;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.Map;

public class PolylineDetailsDialog {

    public static void show(Context context, PolylineResult polylineResult) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        ScrollView scroll = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(40, 40, 40, 40);

        TextView title = new TextView(context);
        title.setText("Wegdetails");
        title.setTextSize(18f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        container.addView(title);

        TextView scoreView = new TextView(context);
        scoreView.setText("Score: " + polylineResult.getScore());
        scoreView.setTextSize(16f);
        scoreView.setPadding(0, 20, 0, 10);
        container.addView(scoreView);

        // Display slope information with appropriate styling
        double maxSlope = polylineResult.getMaxSlopePercent();
        TextView slopeView = new TextView(context);

        if (maxSlope >= 0) {
            slopeView.setText(String.format("Max. helling: %.1f%%", maxSlope));

            // Color code based on slope severity
            if (maxSlope > 10.0) {
                slopeView.setTextColor(Color.RED);
                slopeView.setTypeface(Typeface.DEFAULT_BOLD);
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

        // Add explanation for steep slopes
        if (maxSlope > 10.0) {
            TextView warningView = new TextView(context);
            warningView.setText("⚠️ Zeer steile helling - mogelijk moeilijk berijdbaar");
            warningView.setTextColor(Color.RED);
            warningView.setTextSize(12f);
            warningView.setPadding(0, 0, 0, 15);
            container.addView(warningView);
        } else if (maxSlope > 8.0) {
            TextView warningView = new TextView(context);
            warningView.setText("⚠️ Steile helling - uitdagend");
            warningView.setTextColor(Color.parseColor("#FF6600"));
            warningView.setTextSize(12f);
            warningView.setPadding(0, 0, 0, 15);
            container.addView(warningView);
        }

        // Display OSM tags
        if (polylineResult.getTags() != null && !polylineResult.getTags().isEmpty()) {
            TextView tagsTitle = new TextView(context);
            tagsTitle.setText("OSM Tags:");
            tagsTitle.setTextSize(14f);
            tagsTitle.setTypeface(Typeface.DEFAULT_BOLD);
            tagsTitle.setPadding(0, 10, 0, 5);
            container.addView(tagsTitle);

            for (Map.Entry<String, String> entry : polylineResult.getTags().entrySet()) {
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
        dialog.show();
    }
}