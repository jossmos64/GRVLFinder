package be.kuleuven.gt.grvlfinder;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Legal acceptance screen - REQUIRED before app can be used
 * Complies with Google Play Store and GDPR requirements
 */
public class LegalAcceptanceActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "grvlPrefs";
    private static final String KEY_LEGAL_ACCEPTED = "legalAccepted";

    // Replace this with your actual hosted privacy policy URL
    private static final String PRIVACY_POLICY_URL = "https://grvlfinderbackend.netlify.app/";

    private CheckBox privacyCheckbox;
    private CheckBox termsCheckbox;
    private Button acceptButton;
    private Button declineButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_legal_acceptance);

        initializeViews();
        setupClickListeners();
    }

    private void initializeViews() {
        privacyCheckbox = findViewById(R.id.privacyCheckbox);
        termsCheckbox = findViewById(R.id.termsCheckbox);
        acceptButton = findViewById(R.id.acceptButton);
        declineButton = findViewById(R.id.declineButton);

        TextView privacyLink = findViewById(R.id.privacyPolicyLink);
        TextView termsLink = findViewById(R.id.termsLink);

        // Initially disable accept button
        acceptButton.setEnabled(false);

        // Enable accept button only when both checkboxes are checked
        privacyCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> updateAcceptButton());
        termsCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> updateAcceptButton());

        // Open privacy policy in browser
        privacyLink.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL));
            startActivity(browserIntent);
        });

        // Show full terms dialog
        termsLink.setOnClickListener(v -> showTermsDialog());
    }

    private void setupClickListeners() {
        acceptButton.setOnClickListener(v -> acceptLegal());
        declineButton.setOnClickListener(v -> declineLegal());
    }

    private void updateAcceptButton() {
        acceptButton.setEnabled(privacyCheckbox.isChecked() && termsCheckbox.isChecked());
    }

    private void acceptLegal() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_LEGAL_ACCEPTED, true)
                .apply();

        // Legal accepted - now go to main app
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void declineLegal() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Terms Required")
                .setMessage("You must accept the Privacy Policy and Terms of Use to use GRVLFinder.\n\n" +
                        "Without acceptance, the app cannot function as it requires location access and uses third-party services.\n\n" +
                        "Would you like to review the policies again?")
                .setPositiveButton("Review", (dialog, which) -> {
                    // User wants to review - do nothing, stay on screen
                })
                .setNegativeButton("Exit App", (dialog, which) -> {
                    // User refuses - exit app completely
                    finishAffinity();
                })
                .setCancelable(false)
                .show();
    }

    private void showTermsDialog() {
        // Create a custom view with scrollable TextView
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        TextView textView = new TextView(this);
        textView.setText(getTermsOfUseText());
        textView.setPadding(50, 50, 50, 50);
        textView.setTextSize(14);
        textView.setTextColor(0xFF000000);
        scrollView.addView(textView);

        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(this);

        builder.setTitle("Terms of Use");
        builder.setView(scrollView);
        builder.setPositiveButton("Close", null);

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();
    }

    private String getTermsOfUseText() {
        return "TERMS OF USE FOR GRVLFINDER\n\n" +
                "By using this application, you agree to:\n\n" +
                "1. DATA COLLECTION\n" +
                "• The app accesses your location to provide mapping functionality\n" +
                "• All data is processed locally on your device\n" +
                "• No personal data is transmitted to our servers\n" +
                "• Weather and map data is fetched from third-party services\n\n" +
                "2. THIRD-PARTY SERVICES\n" +
                "• OpenStreetMap for map data\n" +
                "• Open-Meteo for weather information\n" +
                "• OpenTopoData for elevation data\n" +
                "• Strava API (optional, if you connect)\n\n" +
                "3. USER RESPONSIBILITIES\n" +
                "• You are responsible for your own safety while cycling\n" +
                "• Route recommendations are for informational purposes only\n" +
                "• Always verify road conditions before cycling\n" +
                "• Weather data may not be 100% accurate\n\n" +
                "4. LIABILITY\n" +
                "• This app is provided \"as-is\" without warranties\n" +
                "• We are not liable for accidents, injuries, or damages\n" +
                "• Always use proper judgment when cycling\n\n" +
                "5. YOUR RIGHTS (GDPR/CCPA)\n" +
                "• Right to access your data (all stored locally)\n" +
                "• Right to delete data (uninstall app)\n" +
                "• Right to export data (GPX export feature)\n" +
                "• Right to withdraw consent (disable location permission)\n\n" +
                "6. CONTACT\n" +
                "For privacy concerns: grvlfinder@gmail.com\n\n" +
                "By clicking 'I Accept', you confirm you have read and agree to these terms.";
    }

    @Override
    public void onBackPressed() {
        // Prevent back button from bypassing legal acceptance
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Exit App?")
                .setMessage("You must accept the Terms and Privacy Policy to use GRVLFinder.\n\nExit app?")
                .setPositiveButton("Exit", (dialog, which) -> finishAffinity())
                .setNegativeButton("Stay", null)
                .show();
    }
}