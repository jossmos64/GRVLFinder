package be.kuleuven.gt.grvlfinder;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class BikeTypeSelectionDialog {

    public interface BikeTypeSelectionCallback {
        void onBikeTypeSelected(BikeType bikeType);
        void onCustomCriteriaRequested();
        void onElevationSettingChanged(boolean enabled);
    }

    public static void show(Context context, BikeType currentType, boolean elevationEnabled,
                            BikeTypeSelectionCallback callback) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        ScrollView scroll = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        int padding = dpToPx(context, 20);
        container.setPadding(padding, padding, padding, padding);

        // Title
        TextView title = new TextView(context);
        title.setText("Select your bicycle type");
        title.setTextSize(18f);
        title.setTypeface(Typeface.DEFAULT_BOLD);

        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.bottomMargin = dpToPx(context, 16);
        container.addView(title, titleParams);

        // Create elevation checkbox container
        LinearLayout elevationContainer = createElevationCheckbox(context, elevationEnabled, callback);

        // Add bike type options
        for (BikeType bikeType : BikeType.values()) {
            addBikeTypeOption(context, container, bikeType, currentType, callback, dialog);
        }

        // Add elevation checkbox at the bottom
        LinearLayout.LayoutParams checkboxParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        checkboxParams.topMargin = dpToPx(context, 16);
        container.addView(elevationContainer, checkboxParams);

        // Update checkbox visibility based on current bike type
        updateElevationCheckboxVisibility(elevationContainer, currentType);

        scroll.addView(container);
        dialog.setContentView(scroll);
        dialog.show();
    }

    private static LinearLayout createElevationCheckbox(Context context, boolean elevationEnabled,
                                                        BikeTypeSelectionCallback callback) {
        LinearLayout checkboxContainer = new LinearLayout(context);
        checkboxContainer.setOrientation(LinearLayout.VERTICAL);

        CheckBox elevationCheckbox = new CheckBox(context);
        elevationCheckbox.setText("Use elevation data for accurate slopes");
        elevationCheckbox.setTextSize(14f);
        elevationCheckbox.setChecked(elevationEnabled);

        // Add explanation text
        TextView explanation = new TextView(context);
        explanation.setText("(Increases accuracy but makes searching slower)");
        explanation.setTextSize(12f);
        explanation.setTextColor(0xFF666666);

        checkboxContainer.addView(elevationCheckbox);
        checkboxContainer.addView(explanation);

        elevationCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (callback != null) {
                callback.onElevationSettingChanged(isChecked);
            }
        });

        return checkboxContainer;
    }

    private static void addBikeTypeOption(Context context, LinearLayout container,
                                          BikeType bikeType, BikeType currentType,
                                          BikeTypeSelectionCallback callback,
                                          BottomSheetDialog dialog) {

        // Create option container
        LinearLayout optionLayout = new LinearLayout(context);
        optionLayout.setOrientation(LinearLayout.VERTICAL);
        optionLayout.setPadding(dpToPx(context, 16), dpToPx(context, 12),
                dpToPx(context, 16), dpToPx(context, 12));

        // Set background based on selection
        if (bikeType == currentType) {
            optionLayout.setBackgroundResource(android.R.drawable.list_selector_background);
            optionLayout.setBackgroundColor(0x22228B22); // Light green highlight
        } else {
            optionLayout.setBackgroundResource(android.R.drawable.list_selector_background);
        }

        // Main title with emoji
        TextView nameView = new TextView(context);
        nameView.setText(bikeType.getFullDisplayName());
        nameView.setTextSize(16f);
        nameView.setTypeface(Typeface.DEFAULT_BOLD);
        if (bikeType == currentType) {
            nameView.setTextColor(0xFF228B22); // Green for current selection
        }

        // Description
        TextView descView = new TextView(context);
        descView.setText(bikeType.getDescription());
        descView.setTextSize(14f);
        descView.setTextColor(0xFF666666);

        // Elevation info for applicable modes
        TextView elevationInfo = new TextView(context);
        elevationInfo.setTextSize(12f);
        elevationInfo.setTextColor(0xFF888888);

        switch (bikeType) {
            case RACE_ROAD:
            case GRAVEL_BIKE:
                elevationInfo.setText("Height analysis optional");
                break;
            case RACE_BIKEPACKING:
            case GRAVEL_BIKEPACKING:
                elevationInfo.setText("Height analysis always enabled - (makes searching slower)");
                break;
            case CUSTOM:
                elevationInfo.setText("Height analysis depending on settings");
                break;
        }

        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        descParams.topMargin = dpToPx(context, 4);

        LinearLayout.LayoutParams elevationParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        elevationParams.topMargin = dpToPx(context, 2);

        optionLayout.addView(nameView);
        optionLayout.addView(descView, descParams);
        optionLayout.addView(elevationInfo, elevationParams);

        // Add custom criteria button for CUSTOM mode
        if (bikeType == BikeType.CUSTOM) {
            Button customButton = new Button(context);
            customButton.setText("Configure criteria");
            customButton.setTextSize(12f);
            customButton.setBackgroundColor(0xFFDDDDDD);
            customButton.setPadding(dpToPx(context, 8), dpToPx(context, 4),
                    dpToPx(context, 8), dpToPx(context, 4));

            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            buttonParams.topMargin = dpToPx(context, 8);

            customButton.setOnClickListener(v -> {
                if (callback != null) {
                    callback.onCustomCriteriaRequested();
                }
            });

            optionLayout.addView(customButton, buttonParams);
        }

        // Click listener for the whole option
        optionLayout.setOnClickListener(v -> {
            if (callback != null) {
                callback.onBikeTypeSelected(bikeType);
            }
            dialog.dismiss();
        });

        // Add to container
        LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        optionParams.bottomMargin = dpToPx(context, 8);
        container.addView(optionLayout, optionParams);
    }

    private static void updateElevationCheckboxVisibility(LinearLayout elevationContainer, BikeType bikeType) {
        if (elevationContainer == null) return;

        // Show checkbox only for modes where it's relevant
        switch (bikeType) {
            case RACE_ROAD:
            case GRAVEL_BIKE:
            case CUSTOM:
                elevationContainer.setVisibility(View.VISIBLE);
                break;
            case RACE_BIKEPACKING:
            case GRAVEL_BIKEPACKING:
                elevationContainer.setVisibility(View.GONE);
                break;
            default:
                elevationContainer.setVisibility(View.VISIBLE);
                break;
        }
    }

    private static int dpToPx(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}