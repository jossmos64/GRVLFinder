package be.kuleuven.gt.grvlfinder;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import org.osmdroid.util.GeoPoint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class GpxExporter {

    public interface ExportCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public static void exportRoute(Context context, List<GeoPoint> route, String filename, ExportCallback callback) {
        if (route == null || route.isEmpty()) {
            callback.onError("No route to export");
            return;
        }

        if (filename == null || filename.trim().isEmpty()) filename = "route.gpx";
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".gpx")) {
            filename = filename + ".gpx";
        }

        try {
            String gpxContent = generateGpx(route);
            saveGpxToDownloads(context, gpxContent, filename, callback);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onError("Error generating GPX: " + e.getMessage());
        }
    }

    private static String generateGpx(List<GeoPoint> route) {
        // time formatting in UTC ISO 8601
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        String now = iso.format(new Date());

        StringBuilder sb = new StringBuilder(1024);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<gpx version=\"1.1\" creator=\"GravelRideApp\" ")
                .append("xmlns=\"http://www.topografix.com/GPX/1/1\" ")
                .append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
                .append("xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 ")
                .append("http://www.topografix.com/GPX/1/1/gpx.xsd\">\n");

        // metadata
        sb.append("  <metadata>\n");
        sb.append("    <time>").append(now).append("</time>\n");
        sb.append("    <name>Exported route</name>\n");
        sb.append("  </metadata>\n");

        // track
        sb.append("  <trk>\n");
        sb.append("    <name>Route</name>\n");
        sb.append("    <trkseg>\n");

        // assign timestamps incrementally (1 second apart) starting at now
        long baseMillis = System.currentTimeMillis();
        for (int i = 0; i < route.size(); i++) {
            GeoPoint pt = route.get(i);
            long t = baseMillis + i * 1000L;
            String ts = iso.format(new Date(t));
            sb.append(String.format(Locale.US,
                    "      <trkpt lat=\"%f\" lon=\"%f\">\n",
                    pt.getLatitude(), pt.getLongitude()));
            sb.append("        <ele>0.0</ele>\n");
            sb.append("        <time>").append(ts).append("</time>\n");
            sb.append("      </trkpt>\n");
        }

        sb.append("    </trkseg>\n");
        sb.append("  </trk>\n");
        sb.append("</gpx>\n");

        return sb.toString();
    }

    private static void saveGpxToDownloads(Context context, String gpxContent, String filename, ExportCallback callback) {
        try {
            byte[] bytes = gpxContent.getBytes(StandardCharsets.UTF_8);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ : use MediaStore with IS_PENDING
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/gpx+xml");
                // optional subfolder inside Downloads:
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GravelRides");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                Uri itemUri = context.getContentResolver().insert(collection, values);
                if (itemUri == null) throw new Exception("Cannot create file in Downloads (MediaStore returned null)");

                try (OutputStream out = context.getContentResolver().openOutputStream(itemUri)) {
                    if (out == null) throw new Exception("Cannot open output stream to file");
                    out.write(bytes);
                    out.flush();
                }

                // mark as not pending so other apps can see it
                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                context.getContentResolver().update(itemUri, values, null, null);

                callback.onSuccess("GPX saved to Downloads/GravelRides as " + filename);
            } else {
                // Pre-Android 10: write to public Downloads (requires WRITE_EXTERNAL_STORAGE on older devices)
                File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File folder = new File(downloads, "GravelRides");
                if (!folder.exists()) folder.mkdirs();

                File outFile = new File(folder, filename);
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    fos.write(bytes);
                    fos.flush();
                }

                // make visible to other apps
                MediaScannerConnection.scanFile(context,
                        new String[]{outFile.getAbsolutePath()},
                        new String[]{"application/gpx+xml"},
                        null);

                callback.onSuccess("GPX saved to Downloads/GravelRides as " + filename);
            }
        } catch (Exception e) {
            e.printStackTrace();
            callback.onError("Error saving GPX: " + e.getMessage());
        }
    }
}
