package be.kuleuven.gt.grvlfinder;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class LicensesActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_licenses);

        TextView licensesText = findViewById(R.id.licensesText);
        Button backButton = findViewById(R.id.backButton);

        // Load licenses from assets
        licensesText.setText(loadTextFromAssets("THIRD_PARTY_LICENSES.txt"));

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
            return "License information could not be loaded. Please contact grvlfinder@gmail.com";
        }
    }
}