package be.kuleuven.gt.grvlfinder;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class PrivacyPolicyActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_policy);

        TextView privacyText = findViewById(R.id.privacyPolicyText);
        Button viewOnlineButton = findViewById(R.id.viewOnlineButton);
        Button backButton = findViewById(R.id.backButton);

        // Load privacy policy from assets
        privacyText.setText(loadTextFromAssets("PRIVACY_POLICY.md"));

        viewOnlineButton.setOnClickListener(v -> {
            // Replace with your actual hosted privacy policy URL
            String url = "https://grvlfinderbackend.netlify.app/";
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(browserIntent);
        });

        backButton.setOnClickListener(v -> finish());
    }

    private String loadTextFromAssets(String filename) {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(getAssets().open(filename))
            );
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "Privacy policy could not be loaded. Please contact grvlfinder@gmail.com";
        }
    }
}