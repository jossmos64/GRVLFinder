package be.kuleuven.gt.grvlfinder;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.TextView;
import java.io.InputStream;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_about);
        TextView tv = findViewById(R.id.licenseText);

        StringBuilder sb = new StringBuilder();
        sb.append("Licenses & attribution\n\n");
        sb.append("OpenStreetMap data — Open Database License (ODbL). Attribution shown in-app.\n\n");
        sb.append("osmdroid — Apache License 2.0\n\n");
        sb.append("OSRM — use of public demo server is not recommended for production.\n\n");
        sb.append("Third-party libraries: list all libraries and licenses here.\n\n");

        // Optionally read assets/THIRD_PARTY_LICENSES.txt
        try {
            InputStream is = getAssets().open("THIRD_PARTY_LICENSES.txt");
            int n;
            byte[] buf = new byte[4096];
            while ((n = is.read(buf)) > 0) {
                sb.append(new String(buf, 0, n));
            }
            is.close();
        } catch (Exception e) {
            // ignore
        }

        tv.setText(sb.toString());
    }
}
