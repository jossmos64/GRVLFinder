package be.kuleuven.gt.grvlfinder;

import android.os.AsyncTask;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class OSRMRoutingService {
    public static final String DEFAULT_OSRM_URL = "https://router.project-osrm.org/route/v1/bicycle/";

    private Context context;

    public OSRMRoutingService(Context context) {

        this.context = context.getApplicationContext();
    }

    public interface RoutingCallback {
        void onRouteCalculated(List<GeoPoint> route);
    }

    public static class RouteTask extends AsyncTask<GeoPoint, Void, List<GeoPoint>> {
        private RoutingCallback callback;
        private Context context;

        public RouteTask(Context ctx, RoutingCallback callback) {
            this.callback = callback;
            this.context = ctx.getApplicationContext();
        }

        @Override
        protected List<GeoPoint> doInBackground(GeoPoint... points) {
            if (points.length != 2) return createFallbackRoute(points[0], points[1]);

            try {
                GeoPoint start = points[0];
                GeoPoint end = points[1];

                // Read base URL from prefs
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                String base = prefs.getString("routing_base_url", DEFAULT_OSRM_URL);
                if (!base.endsWith("/")) base = base + "/";
                // If user provided an API key param (optional)
                String apiKeyParam = prefs.getString("routing_api_key_param", "");
                String coords = start.getLongitude() + "," + start.getLatitude() + ";" +
                        end.getLongitude() + "," + end.getLatitude();

                String urlStr = base + coords + "?overview=full&geometries=geojson" + apiKeyParam;

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    sb.append(line);
                }
                in.close();

                JSONObject root = new JSONObject(sb.toString());
                JSONArray routes = root.optJSONArray("routes");

                if (routes == null || routes.length() == 0) {
                    return createFallbackRoute(start, end);
                }

                JSONArray coordsArr = routes.getJSONObject(0)
                        .getJSONObject("geometry")
                        .getJSONArray("coordinates");

                List<GeoPoint> routePoints = new ArrayList<>();
                for (int i = 0; i < coordsArr.length(); i++) {
                    JSONArray coord = coordsArr.getJSONArray(i);
                    routePoints.add(new GeoPoint(coord.getDouble(1), coord.getDouble(0)));
                }

                return routePoints;

            } catch (Exception e) {
                e.printStackTrace();
                return createFallbackRoute(points[0], points[1]);
            }
        }

        private List<GeoPoint> createFallbackRoute(GeoPoint start, GeoPoint end) {
            List<GeoPoint> fallback = new ArrayList<>();
            fallback.add(start);
            fallback.add(end);
            return fallback;
        }

        @Override
        protected void onPostExecute(List<GeoPoint> route) {
            if (callback != null) {
                callback.onRouteCalculated(route);
            }
        }
    }
}
