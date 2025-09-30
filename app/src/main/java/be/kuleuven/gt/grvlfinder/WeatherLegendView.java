package be.kuleuven.gt.grvlfinder;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Modern weather status indicator for the legend
 */
public class WeatherLegendView {

    /**
     * Create a modern weather status view with card-like appearance
     */
    public static LinearLayout create(Context context) {
        LinearLayout weatherLayout = new LinearLayout(context);
        weatherLayout.setOrientation(LinearLayout.HORIZONTAL);
        weatherLayout.setPadding(12, 8, 12, 8);
        weatherLayout.setGravity(Gravity.CENTER_VERTICAL);

        // Modern card background
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(12);
        background.setStroke(2, Color.parseColor("#E0E0E0"));
        weatherLayout.setBackground(background);

        // Weather icon container
        LinearLayout iconContainer = new LinearLayout(context);
        iconContainer.setOrientation(LinearLayout.VERTICAL);
        iconContainer.setGravity(Gravity.CENTER);
        iconContainer.setPadding(8, 4, 12, 4);

        TextView weatherIcon = new TextView(context);
        weatherIcon.setText("☁️");
        weatherIcon.setTextSize(24);
        weatherIcon.setGravity(Gravity.CENTER);
        weatherIcon.setId(android.R.id.icon); // For easy reference
        iconContainer.addView(weatherIcon);

        weatherLayout.addView(iconContainer);

        // Text container
        LinearLayout textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        textContainer.setLayoutParams(textParams);

        TextView weatherLabel = new TextView(context);
        weatherLabel.setText("Weather");
        weatherLabel.setTextSize(10);
        weatherLabel.setTextColor(Color.parseColor("#9E9E9E"));
        weatherLabel.setTypeface(null, Typeface.BOLD);
        textContainer.addView(weatherLabel);

        TextView weatherText = new TextView(context);
        weatherText.setText("Press Find Gravel");
        weatherText.setTextSize(12);
        weatherText.setTextColor(Color.parseColor("#424242"));
        weatherText.setId(android.R.id.text1); // For easy reference
        textContainer.addView(weatherText);

        weatherLayout.addView(textContainer);

        // Status indicator dot
        TextView statusDot = new TextView(context);
        statusDot.setText("●");
        statusDot.setTextSize(20);
        statusDot.setTextColor(Color.parseColor("#BDBDBD"));
        statusDot.setPadding(8, 0, 4, 0);
        statusDot.setId(android.R.id.text2); // For status dot
        weatherLayout.addView(statusDot);

        return weatherLayout;
    }

    /**
     * Update the weather status display with modern styling
     */
    public static void updateWeatherStatus(LinearLayout weatherView,
                                           WeatherService.WeatherCondition condition) {
        if (weatherView == null) return;

        TextView weatherIcon = weatherView.findViewById(android.R.id.icon);
        TextView weatherText = weatherView.findViewById(android.R.id.text1);
        TextView statusDot = weatherView.findViewById(android.R.id.text2);

        // Update background color based on condition
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(12);
        background.setStroke(2, Color.parseColor("#E0E0E0"));

        if (condition == null) {
            weatherIcon.setText("☁️");
            weatherText.setText("No data");
            weatherText.setTextColor(Color.parseColor("#9E9E9E"));
            statusDot.setTextColor(Color.parseColor("#BDBDBD"));
            background.setColor(Color.WHITE);
        } else if (condition.isMuddy) {
            weatherIcon.setText("🌧️");
            weatherText.setText(String.format("Muddy (%d days)", condition.rainyDaysCount));
            weatherText.setTextColor(Color.parseColor("#FF6B35"));
            weatherText.setTypeface(null, Typeface.BOLD);
            statusDot.setTextColor(Color.parseColor("#FF6B35"));

            // Subtle warning background
            background.setColor(Color.parseColor("#FFF3E0"));
            background.setStroke(2, Color.parseColor("#FFB74D"));
        } else {
            weatherIcon.setText("☀️");
            weatherText.setText("Good");
            weatherText.setTextColor(Color.parseColor("#4CAF50"));
            weatherText.setTypeface(null, Typeface.BOLD);
            statusDot.setTextColor(Color.parseColor("#4CAF50"));

            // Subtle success background
            background.setColor(Color.parseColor("#E8F5E9"));
            background.setStroke(2, Color.parseColor("#81C784"));
        }

        weatherView.setBackground(background);
    }

    /**
     * Set loading state with animation effect
     */
    public static void setLoading(LinearLayout weatherView, boolean loading) {
        if (weatherView == null) return;

        TextView weatherText = weatherView.findViewById(android.R.id.text1);
        TextView weatherIcon = weatherView.findViewById(android.R.id.icon);
        TextView statusDot = weatherView.findViewById(android.R.id.text2);

        if (loading) {
            weatherIcon.setText("⏳");
            weatherText.setText("Loading...");
            weatherText.setTextColor(Color.parseColor("#757575"));
            statusDot.setTextColor(Color.parseColor("#BDBDBD"));

            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.WHITE);
            background.setCornerRadius(12);
            background.setStroke(2, Color.parseColor("#E0E0E0"));
            weatherView.setBackground(background);
        }
    }

    /**
     * Set "not loaded yet" state with helpful message
     */
    public static void setNotLoadedYet(LinearLayout weatherView) {
        if (weatherView == null) return;

        TextView weatherIcon = weatherView.findViewById(android.R.id.icon);
        TextView weatherText = weatherView.findViewById(android.R.id.text1);
        TextView statusDot = weatherView.findViewById(android.R.id.text2);

        weatherIcon.setText("🔍");
        weatherText.setText("Press Find Gravel");
        weatherText.setTextSize(10);
        weatherText.setTextColor(Color.parseColor("#757575"));

        statusDot.setTextColor(Color.parseColor("#BDBDBD"));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#FAFAFA"));
        background.setCornerRadius(12);
        background.setStroke(2, Color.parseColor("#E0E0E0"));
        weatherView.setBackground(background);
    }

    /**
     * Add pulse animation to weather view (for new data arrival)
     */
    public static void animateUpdate(LinearLayout weatherView) {
        if (weatherView == null) return;

        weatherView.animate()
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(150)
                .withEndAction(() -> {
                    weatherView.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(150)
                            .start();
                })
                .start();
    }
}