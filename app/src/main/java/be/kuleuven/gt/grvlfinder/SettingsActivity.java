package be.kuleuven.gt.grvlfinder;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        TextView appVersion = findViewById(R.id.appVersion);
        appVersion.setText("Version 1.0.0");

        Button btnPrivacy = findViewById(R.id.btnPrivacyPolicy);
        Button btnLicenses = findViewById(R.id.btnThirdPartyLicenses);
        Button btnBack = findViewById(R.id.btnBack);

        btnPrivacy.setOnClickListener(v -> {
            Intent intent = new Intent(this, PrivacyPolicyActivity.class);
            startActivity(intent);
        });

        btnLicenses.setOnClickListener(v -> {
            Intent intent = new Intent(this, LicensesActivity.class);
            startActivity(intent);
        });

        btnBack.setOnClickListener(v -> finish());
    }
}