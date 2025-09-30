package be.kuleuven.gt.grvlfinder;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.card.MaterialCardView;

/**
 * Modern, visually appealing weather warning dialog
 */
public class WeatherWarningDialog {

    public static void show(Context context, WeatherService.WeatherCondition condition) {
        if (condition == null) {
            showError(context, "No weather data available");
            return;
        }

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout mainLayout = new LinearLayout(context);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(0, 0, 0, 0);
        mainLayout.setBackgroundColor(Color.parseColor("#F5F5F5"));

        // Header with gradient background
        LinearLayout header = createHeader(context, condition);
        mainLayout.addView(header);

        // Content area
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(24, 24, 24, 24);

        // Summary cards
        content.addView(createSummaryCard(context, condition));
        content.addView(createSpacing(context, 16));

        // Daily breakdown
        content.addView(createDailyBreakdownCard(context, condition));
        content.addView(createSpacing(context, 16));

        // Impact card
        content.addView(createImpactCard(context, condition));
        content.addView(createSpacing(context, 24));

        // Close button
        content.addView(createCloseButton(context, dialog));

        mainLayout.addView(content);
        dialog.setContentView(mainLayout);

        // Make dialog width match parent with margins
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialog.show();
    }

    private static LinearLayout createHeader(Context context, WeatherService.WeatherCondition condition) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(32, 40, 32, 40);
        header.setGravity(Gravity.CENTER);

        // Set gradient background
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                condition.isMuddy ?
                        new int[]{Color.parseColor("#FF6B35"), Color.parseColor("#F7931E")} :
                        new int[]{Color.parseColor("#4CAF50"), Color.parseColor("#8BC34A")}
        );
        header.setBackground(gradient);

        // Weather icon
        TextView icon = new TextView(context);
        icon.setText(condition.isMuddy ? "🌧️" : "☀️");
        icon.setTextSize(64);
        icon.setGravity(Gravity.CENTER);
        header.addView(icon);

        // Title
        TextView title = new TextView(context);
        title.setText(condition.isMuddy ? "Muddy Conditions" : "Good Conditions");
        title.setTextSize(24);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 16, 0, 8);
        header.addView(title);

        // Subtitle
        TextView subtitle = new TextView(context);
        subtitle.setText(String.format("Based on %d days analysis", 7));
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.parseColor("#FFFFFF"));
        subtitle.setGravity(Gravity.CENTER);
        header.addView(subtitle);

        return header;
    }

    private static MaterialCardView createSummaryCard(Context context,
                                                      WeatherService.WeatherCondition condition) {
        MaterialCardView card = createCard(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(20, 20, 20, 20);

        // Card title
        TextView cardTitle = new TextView(context);
        cardTitle.setText("Precipitation Summary");
        cardTitle.setTextSize(16);
        cardTitle.setTypeface(null, Typeface.BOLD);
        cardTitle.setTextColor(Color.parseColor("#212121"));
        content.addView(cardTitle);

        content.addView(createSpacing(context, 16));

        // Stats row
        LinearLayout statsRow = new LinearLayout(context);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);

        // Rainy days stat
        LinearLayout rainyDaysBox = createStatBox(context,
                String.valueOf(condition.rainyDaysCount),
                "Rainy Days",
                condition.isMuddy ? "#FF6B35" : "#4CAF50"
        );
        statsRow.addView(rainyDaysBox);

        // Spacer
        View spacer = new View(context);
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
                0, 1, 1.0f
        );
        spacer.setLayoutParams(spacerParams);
        statsRow.addView(spacer);

        // Total precipitation stat
        LinearLayout precipBox = createStatBox(context,
                String.format("%.1f mm", condition.totalPrecipitationMm),
                "Total Rain",
                "#2196F3"
        );
        statsRow.addView(precipBox);

        content.addView(statsRow);
        card.addView(content);
        return card;
    }

    private static LinearLayout createStatBox(Context context, String value,
                                              String label, String color) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(16, 16, 16, 16);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(color + "20")); // 20 = alpha
        bg.setCornerRadius(12);
        box.setBackground(bg);

        TextView valueText = new TextView(context);
        valueText.setText(value);
        valueText.setTextSize(24);
        valueText.setTypeface(null, Typeface.BOLD);
        valueText.setTextColor(Color.parseColor(color));
        valueText.setGravity(Gravity.CENTER);
        box.addView(valueText);

        TextView labelText = new TextView(context);
        labelText.setText(label);
        labelText.setTextSize(12);
        labelText.setTextColor(Color.parseColor("#757575"));
        labelText.setGravity(Gravity.CENTER);
        labelText.setPadding(0, 4, 0, 0);
        box.addView(labelText);

        return box;
    }

    private static MaterialCardView createDailyBreakdownCard(Context context,
                                                             WeatherService.WeatherCondition condition) {
        MaterialCardView card = createCard(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(20, 20, 20, 20);

        TextView cardTitle = new TextView(context);
        cardTitle.setText("7-Day Breakdown");
        cardTitle.setTextSize(16);
        cardTitle.setTypeface(null, Typeface.BOLD);
        cardTitle.setTextColor(Color.parseColor("#212121"));
        content.addView(cardTitle);

        content.addView(createSpacing(context, 12));

        // Add daily bars
        for (WeatherService.DailyWeather day : condition.dailyData) {
            content.addView(createDayBar(context, day));
            content.addView(createSpacing(context, 8));
        }

        card.addView(content);
        return card;
    }

    private static LinearLayout createDayBar(Context context, WeatherService.DailyWeather day) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        // Date label
        TextView dateText = new TextView(context);
        String shortDate = day.date.substring(5); // MM-DD
        dateText.setText(shortDate);
        dateText.setTextSize(12);
        dateText.setTextColor(Color.parseColor("#616161"));
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                80, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        dateText.setLayoutParams(dateParams);
        row.addView(dateText);

        // Progress bar container
        LinearLayout barContainer = new LinearLayout(context);
        barContainer.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams barContainerParams = new LinearLayout.LayoutParams(
                0, 24, 1.0f
        );
        barContainerParams.setMargins(8, 0, 8, 0);
        barContainer.setLayoutParams(barContainerParams);

        GradientDrawable barBg = new GradientDrawable();
        barBg.setColor(Color.parseColor("#E0E0E0"));
        barBg.setCornerRadius(12);
        barContainer.setBackground(barBg);

        // Progress bar fill
        View fill = new View(context);
        double maxPrecip = 30.0; // Max for scaling
        double width = Math.min(day.precipitationMm / maxPrecip, 1.0);
        LinearLayout.LayoutParams fillParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, (float)width
        );
        fill.setLayoutParams(fillParams);

        GradientDrawable fillBg = new GradientDrawable();
        fillBg.setColor(day.isRainy ?
                Color.parseColor("#2196F3") : Color.parseColor("#BDBDBD"));
        fillBg.setCornerRadius(12);
        fill.setBackground(fillBg);
        barContainer.addView(fill);

        // Empty space
        View empty = new View(context);
        LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, (float)(1.0 - width)
        );
        empty.setLayoutParams(emptyParams);
        barContainer.addView(empty);

        row.addView(barContainer);

        // Value label
        TextView valueText = new TextView(context);
        valueText.setText(String.format("%.1f", day.precipitationMm));
        valueText.setTextSize(12);
        valueText.setTextColor(day.isRainy ?
                Color.parseColor("#2196F3") : Color.parseColor("#9E9E9E"));
        if (day.isRainy) {
            valueText.setTypeface(null, Typeface.BOLD);
        }
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                60, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        valueText.setLayoutParams(valueParams);
        valueText.setGravity(Gravity.END);
        row.addView(valueText);

        return row;
    }

    private static MaterialCardView createImpactCard(Context context,
                                                     WeatherService.WeatherCondition condition) {
        MaterialCardView card = createCard(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(20, 20, 20, 20);

        TextView cardTitle = new TextView(context);
        cardTitle.setText("Road Impact");
        cardTitle.setTextSize(16);
        cardTitle.setTypeface(null, Typeface.BOLD);
        cardTitle.setTextColor(Color.parseColor("#212121"));
        content.addView(cardTitle);

        content.addView(createSpacing(context, 12));

        // Impact items
        content.addView(createImpactItem(context, "Dirt/Earth Roads",
                condition.isMuddy ? "High mud risk" : "Good conditions",
                condition.isMuddy ? "🔴" : "🟢"));

        content.addView(createSpacing(context, 8));

        content.addView(createImpactItem(context, "Gravel Roads",
                condition.isMuddy ? "Moderate mud risk" : "Good conditions",
                condition.isMuddy ? "🟠" : "🟢"));

        content.addView(createSpacing(context, 8));

        content.addView(createImpactItem(context, "Paved Roads",
                "No impact", "🟢"));

        card.addView(content);
        return card;
    }

    private static LinearLayout createImpactItem(Context context, String title,
                                                 String status, String indicator) {
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);

        TextView indicatorText = new TextView(context);
        indicatorText.setText(indicator);
        indicatorText.setTextSize(16);
        item.addView(indicatorText);

        LinearLayout textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        textParams.setMargins(12, 0, 0, 0);
        textContainer.setLayoutParams(textParams);

        TextView titleText = new TextView(context);
        titleText.setText(title);
        titleText.setTextSize(14);
        titleText.setTypeface(null, Typeface.BOLD);
        titleText.setTextColor(Color.parseColor("#424242"));
        textContainer.addView(titleText);

        TextView statusText = new TextView(context);
        statusText.setText(status);
        statusText.setTextSize(12);
        statusText.setTextColor(Color.parseColor("#757575"));
        textContainer.addView(statusText);

        item.addView(textContainer);
        return item;
    }

    private static TextView createCloseButton(Context context, Dialog dialog) {
        TextView button = new TextView(context);
        button.setText("Close");
        button.setTextSize(16);
        button.setTypeface(null, Typeface.BOLD);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 16, 0, 16);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#2196F3"));
        bg.setCornerRadius(24);
        button.setBackground(bg);

        button.setOnClickListener(v -> dialog.dismiss());

        return button;
    }

    private static MaterialCardView createCard(Context context) {
        MaterialCardView card = new MaterialCardView(context);
        card.setCardElevation(4);
        card.setRadius(16);
        card.setCardBackgroundColor(Color.WHITE);
        return card;
    }

    private static View createSpacing(Context context, int dp) {
        View spacer = new View(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int)(dp * context.getResources().getDisplayMetrics().density)
        );
        spacer.setLayoutParams(params);
        return spacer;
    }

    public static void showError(Context context, String error) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        layout.setBackgroundColor(Color.WHITE);

        TextView icon = new TextView(context);
        icon.setText("❌");
        icon.setTextSize(48);
        icon.setGravity(Gravity.CENTER);
        layout.addView(icon);

        TextView title = new TextView(context);
        title.setText("Weather Data Unavailable");
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 16, 0, 8);
        layout.addView(title);

        TextView message = new TextView(context);
        message.setText(error);
        message.setTextSize(14);
        message.setGravity(Gravity.CENTER);
        layout.addView(message);

        layout.addView(createSpacing(context, 24));
        layout.addView(createCloseButton(context, dialog));

        dialog.setContentView(layout);
        dialog.show();
    }

    /**
     * Show "not loaded yet" dialog with helpful instructions
     */
    public static void showNotLoadedYet(Context context) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        layout.setBackgroundColor(Color.WHITE);

        // Icon
        TextView icon = new TextView(context);
        icon.setText("🔍");
        icon.setTextSize(64);
        icon.setGravity(Gravity.CENTER);
        layout.addView(icon);

        // Title
        TextView title = new TextView(context);
        title.setText("Weather Data Not Loaded");
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#424242"));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 16, 0, 8);
        layout.addView(title);

        // Message
        TextView message = new TextView(context);
        message.setText("Press the \"Find Gravel\" button to load weather data for your current map area.");
        message.setTextSize(14);
        message.setTextColor(Color.parseColor("#757575"));
        message.setGravity(Gravity.CENTER);
        message.setPadding(16, 8, 16, 8);
        layout.addView(message);

        layout.addView(createSpacing(context, 16));

        // Info card
        MaterialCardView infoCard = createCard(context);
        LinearLayout infoContent = new LinearLayout(context);
        infoContent.setOrientation(LinearLayout.VERTICAL);
        infoContent.setPadding(20, 16, 20, 16);

        TextView infoText = new TextView(context);
        infoText.setText("💡 Weather data helps identify muddy road conditions after rain");
        infoText.setTextSize(12);
        infoText.setTextColor(Color.parseColor("#616161"));
        infoText.setGravity(Gravity.CENTER);
        infoContent.addView(infoText);

        infoCard.addView(infoContent);
        layout.addView(infoCard);

        layout.addView(createSpacing(context, 24));
        layout.addView(createCloseButton(context, dialog));

        dialog.setContentView(layout);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialog.show();
    }
}