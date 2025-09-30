package be.kuleuven.gt.grvlfinder;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "grvlPrefs";
    private static final String KEY_FIRST_LAUNCH = "firstLaunch";
    private static final String KEY_LEGAL_ACCEPTED = "legalAccepted";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true);
        boolean legalAccepted = prefs.getBoolean(KEY_LEGAL_ACCEPTED, false);

        // Check if this is a returning user who has already accepted legal terms
        if (!isFirstLaunch && legalAccepted) {
            // Returning user - go directly to MainActivity
            startMainActivity();
            return;
        }

        // First time user - show splash screen
        setContentView(R.layout.activity_splash);

        Button btnGo = findViewById(R.id.goButton);
        btnGo.setOnClickListener(v -> {
            // Mark that splash has been seen
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
            // Go to tutorial
            startTutorialActivity();
        });
    }

    private void startTutorialActivity() {
        Intent intent = new Intent(SplashActivity.this, TutorialActivity.class);
        startActivity(intent);
        finish();
    }

    private void startMainActivity() {
        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}