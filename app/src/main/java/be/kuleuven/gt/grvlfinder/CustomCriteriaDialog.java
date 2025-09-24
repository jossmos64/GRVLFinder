package be.kuleuven.gt.grvlfinder;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.Map;

public class CustomCriteriaDialog {

    public interface CustomCriteriaCallback {
        void onCriteriaSaved(Map<String, Integer> weights);
    }

    public static void show(Context context, Map<String, Integer> currentWeights,
                            CustomCriteriaCallback callback) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        ScrollView scroll = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        int padding = dpToPx(context, 20);
        container.setPadding(padding, padding, padding, padding);

        // Title
        TextView title = new TextView(context);
        title.setText("Custom Criteria Configuration");
        title.setTextSize(18f);
        title.setTypeface(Typeface.DEFAULT_BOLD);

        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.bottomMargin = dpToPx(context, 16);
        container.addView(title, titleParams);

        // Instructions
        TextView instructions = new TextView(context);
        instructions.setText("Configure your scoring criteria. Higher values give more weight to that factor.");
        instructions.setTextSize(14f);
        instructions.setTextColor(0xFF666666);

        LinearLayout.LayoutParams instrParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        instrParams.bottomMargin = dpToPx(context, 16);
        container.addView(instructions, instrParams);

        // Create a copy of weights to modify
        Map<String, Integer> tempWeights = new java.util.HashMap<>(currentWeights);

        // Add sliders
        addSlider(container, "surface", "Surface Type", tempWeights,
                "Gravel roads score higher, asphalt roads score lower");
        addSlider(container, "smoothness", "Surface Smoothness", tempWeights,
                "Rewards smoother surfaces, penalizes rough surfaces");
        addSlider(container, "tracktype", "Track Grade", tempWeights,
                "Grade 2-3 tracks score higher, Grade 1 (best) scores lower");
        addSlider(container, "bicycle", "Bicycle Access", tempWeights,
                "Rewards explicit bicycle access, penalizes restrictions");
        addSlider(container, "width", "Path Width", tempWeights,
                "Wider paths score higher, very narrow paths score lower");
        addSlider(container, "length", "Segment Length", tempWeights,
                "Longer segments score higher, very short segments score lower");
        addSlider(container, "slope", "Slope Penalty", tempWeights,
                "Penalizes steep slopes (12%+). Set to 0 to ignore slopes.");

        // Buttons
        LinearLayout buttonLayout = new LinearLayout(context);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setWeightSum(2);

        Button cancelButton = new Button(context);
        cancelButton.setText("Cancel");
        cancelButton.setBackgroundColor(0xFFAAAAAA);
        cancelButton.setTextColor(0xFFFFFFFF);

        Button saveButton = new Button(context);
        saveButton.setText("Save & Apply");
        saveButton.setBackgroundColor(0xFFD0B58A);
        saveButton.setTextColor(0xFFFFFFFF);

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        buttonParams.setMargins(dpToPx(context, 4), dpToPx(context, 16),
                dpToPx(context, 4), 0);

        buttonLayout.addView(cancelButton, buttonParams);
        buttonLayout.addView(saveButton, buttonParams);

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        saveButton.setOnClickListener(v -> {
            if (callback != null) {
                callback.onCriteriaSaved(tempWeights);
            }
            dialog.dismiss();
        });

        container.addView(buttonLayout);
        scroll.addView(container);
        dialog.setContentView(scroll);
        dialog.show();
    }

    private static void addSlider(LinearLayout parent, String key, String displayName,
                                  Map<String, Integer> weights, String description) {
        Context context = parent.getContext();
        int currentVal = weights.getOrDefault(key, 0);

        // Main label
        TextView label = new TextView(context);
        label.setText(displayName + " (" + currentVal + ")");
        label.setTextSize(15f);
        label.setTypeface(Typeface.DEFAULT_BOLD);

        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelParams.topMargin = dpToPx(context, 16);
        parent.addView(label, labelParams);

        // Description
        TextView descText = new TextView(context);
        descText.setText(description);
        descText.setTextSize(12f);
        descText.setTextColor(0xFF666666);

        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        descParams.topMargin = dpToPx(context, 2);
        parent.addView(descText, descParams);

        // Seek bar
        SeekBar seekBar = new SeekBar(context);
        seekBar.setMax(15); // Allow higher values for custom mode
        seekBar.setProgress(Math.max(0, Math.min(15, currentVal)));

        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        seekParams.topMargin = dpToPx(context, 8);
        parent.addView(seekBar, seekParams);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    weights.put(key, progress);
                    label.setText(displayName + " (" + progress + ")");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private static int dpToPx(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}