package be.kuleuven.gt.grvlfinder;

import android.Manifest;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseMapActivity extends AppCompatActivity {

    protected static final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    protected MapView map;
    protected MyLocationNewOverlay locationOverlay;
    protected SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
    }

    protected void initializeMap(MapView mapView) {
        this.map = mapView;

        map.setMultiTouchControls(true);
        map.setBuiltInZoomControls(false);

        // Start met Europa
        GeoPoint europeCenter = new GeoPoint(50.0, 10.0);
        map.getController().setZoom(5.0);
        map.getController().setCenter(europeCenter);

        enableMyLocation();

        if (locationOverlay != null) {
            locationOverlay.runOnFirstFix(() -> {
                runOnUiThread(() -> {
                    GeoPoint myLocation = locationOverlay.getMyLocation();
                    if (myLocation != null) {
                        map.getController().animateTo(myLocation); // centreer op locatie

                        // Vloeiend inzoomen van huidig zoom 6 naar 16
                        new Thread(() -> {
                            for (int z = 6; z <= 16; z++) {
                                int zoom = z;
                                runOnUiThread(() -> map.getController().setZoom(zoom));
                                try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                            }
                        }).start();
                    }
                });
            });
        }
        requestPermissionsIfNecessary();
    }




    private void requestPermissionsIfNecessary() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.INTERNET
        };

        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(perm);
            }
        }

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    listPermissionsNeeded.toArray(new String[0]),
                    REQUEST_PERMISSIONS_REQUEST_CODE
            );
        } else {
            enableMyLocation();
        }
    }

    protected void enableMyLocation() {
        if (locationOverlay == null && map != null) {
            locationOverlay = new MyLocationNewOverlay(map);
            locationOverlay.enableMyLocation();
            map.getOverlays().add(locationOverlay);
            map.invalidate();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            enableMyLocation();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (map != null) map.onPause();
    }
}
