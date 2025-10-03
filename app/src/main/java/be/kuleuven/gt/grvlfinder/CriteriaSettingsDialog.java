package be.kuleuven.gt.grvlfinder;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.Map;

public class CriteriaSettingsDialog {

    public static void show(Context context, Map<String, Integer> weights) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        ScrollView scroll = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        int padding = dpToPx(context, 20);
        container.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(context);
        title.setText("Criteria settings");
        title.setTextSize(18f);
        title.setTypeface(Typeface.DEFAULT_BOLD);

        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.bottomMargin = dpToPx(context, 16);
        container.addView(title, titleParams);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        addSlider(container, "surface", "surface", weights, prefs);
        addSlider(container, "smoothness", "smoothness", weights, prefs);
        addSlider(container, "tracktype", "tracktype", weights, prefs);
        addSlider(container, "bicycle", "bicycle", weights, prefs);
        addSlider(container, "width", "width", weights, prefs);
        addSlider(container, "length", "length", weights, prefs);
        addSlider(container, "slope", "slope", weights, prefs);

        scroll.addView(container);
        dialog.setContentView(scroll);
        dialog.show();
    }

    private static void addSlider(LinearLayout parent, String key, String displayName,
                                  Map<String, Integer> weights, SharedPreferences prefs) {
        Context context = parent.getContext();
        int currentVal = weights.getOrDefault(key, 0);

        TextView label = new TextView(context);
        label.setText(displayName + " score (" + currentVal + ")");
        label.setTextSize(14f);

        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelParams.topMargin = dpToPx(context, 8);
        parent.addView(label, labelParams);

        SeekBar seekBar = new SeekBar(context);
        seekBar.setMax(10);
        seekBar.setProgress(currentVal);

        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        seekParams.topMargin = dpToPx(context, 4);
        seekParams.bottomMargin = dpToPx(context, 8);
        parent.addView(seekBar, seekParams);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    weights.put(key, progress);
                    label.setText(displayName + " score (" + progress + ")");
                    prefs.edit().putInt("weight_" + key, progress).apply();
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