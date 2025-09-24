package be.kuleuven.gt.grvlfinder;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

public class LegendView {
    public static View create(Context context) {
        return LayoutInflater.from(context).inflate(R.layout.view_legend, null, false);
    }

    /**
     * Update legend title and labels based on bike type
     */
    public static void updateForBikeType(View legendView, BikeType bikeType) {
        if (legendView == null) return;

        TextView legendTitle = legendView.findViewById(R.id.legendTitle);
        TextView greenLabel = legendView.findViewById(R.id.greenLabel);
        TextView yellowLabel = legendView.findViewById(R.id.yellowLabel);
        TextView redLabel = legendView.findViewById(R.id.redLabel);

        if (legendTitle == null || greenLabel == null || yellowLabel == null || redLabel == null) {
            return;
        }

        switch (bikeType) {
            case RACE_ROAD:
                legendTitle.setText("🚴‍♂️ Race Roads");
                greenLabel.setText("  Excellent Roads");
                yellowLabel.setText("  Good Roads");
                redLabel.setText("  Avoid");
                break;

            case GRAVEL_BIKE:
                legendTitle.setText("🚵‍♂️ Gravel Paths");
                greenLabel.setText("  Prime Gravel");
                yellowLabel.setText("  Good Gravel");
                redLabel.setText("  Poor Surface");
                break;

            case RACE_BIKEPACKING:
                legendTitle.setText("🎒🚴‍♂️ Touring Roads");
                greenLabel.setText("  Easy Touring");
                yellowLabel.setText("  Manageable");
                redLabel.setText("  Too Steep/Rough");
                break;

            case GRAVEL_BIKEPACKING:
                legendTitle.setText("🎒🚵‍♂️ Adventure Routes");
                greenLabel.setText("  Great Adventure");
                yellowLabel.setText("  Moderate Challenge");
                redLabel.setText("  Extreme/Unrideable");
                break;

            case CUSTOM:
                legendTitle.setText("⚙️ Custom Criteria");
                greenLabel.setText("  High Score");
                yellowLabel.setText("  Medium Score");
                redLabel.setText("  Low Score");
                break;

            default:
                legendTitle.setText("Gravel Legend");
                greenLabel.setText("  Good Gravel");
                yellowLabel.setText("  OK");
                redLabel.setText("  No Go Zone");
                break;
        }
    }
}