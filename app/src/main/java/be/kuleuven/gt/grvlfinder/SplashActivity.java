package be.kuleuven.gt.grvlfinder;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "grvlPrefs";
    private static final String KEY_FIRST_LAUNCH = "firstLaunch";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true);

        if (!isFirstLaunch) {
            // Not first time - go directly to MainActivity
            startMainActivity();
            return;
        }

        // First time - show splash with logo and Go button
        setContentView(R.layout.activity_splash);

        Button btnGo = findViewById(R.id.goButton);
        btnGo.setOnClickListener(v -> {
            // Mark that app has been opened
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
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