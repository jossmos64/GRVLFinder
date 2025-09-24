package be.kuleuven.gt.grvlfinder;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;

public class PrivacyPolicyActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_privacy);
        TextView tv = findViewById(R.id.privacyText);
        tv.setText(loadPrivacyPolicy());

        // Voor publicatie moet je dit beleidsdocument reviewen en aanpassen.
        String policy = "Privacy Policy\n\n" +
                "Last updated: YYYY-MM-DD\n\n" +
                "We collect no personal data except optional location data when you choose to use map features.\n\n" +
                "Data usage:\n" +
                "- Location: used to show your position and for route drawing. Location data is not transmitted or stored by default.\n" +
                "- Exports: GPX exports are saved to your device storage.\n\n" +
                "Third-party services:\n" +
                "- Map tiles and data: OpenStreetMap contributors (ODbL). Attribution is shown in-app.\n" +
                "- Routing: user-configurable provider; by default the app uses router.project-osrm.org (demo). We recommend providing your own API key / provider for production.\n\n" +
                "Contact: add your contact/email here.\n";

        tv.setText(policy);
    }

    private String loadPrivacyPolicy() {
        try {
            InputStream is = getAssets().open("PRIVACY_POLICY.md");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            return new String(buffer, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
            return "Privacy Policy niet beschikbaar.";
        }
    }



}
