package be.kuleuven.gt.grvlfinder;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        TextView tvAppName = findViewById(R.id.appName);
        tvAppName.setText("GRVLFinder");

        Button btnPrivacy = findViewById(R.id.btnPrivacyPolicy);
        Button btnLicenses = findViewById(R.id.btnThirdPartyLicenses);

        btnPrivacy.setOnClickListener(v -> {
            startActivity(new Intent(this, PrivacyPolicyActivity.class));
        });

        btnLicenses.setOnClickListener(v -> {
            // Open licenses dialog
            showLicensesDialog();
        });
    }

    private void showLicensesDialog() {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(getAssets().open("THIRD_PARTY_LICENSES.txt"))
            );
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();

            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
            builder.setTitle("Third-Party Licenses");
            builder.setMessage(sb.toString());
            builder.setPositiveButton("OK", null);
            builder.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
