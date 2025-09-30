package be.kuleuven.gt.grvlfinder;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class StravaApiManager {
    private static final String TAG = "StravaApiManager";
    private static final String CLIENT_ID = "178588";
    private static final String REDIRECT_URI = "http://localhost/exchange_token";
    private static final String BASE_URL = "https://www.strava.com/api/v3";
    private static final String AUTH_URL = "https://www.strava.com/oauth/authorize";
    private static final String BACKEND_TOKEN_URL = "https://grvlfinderbackend.netlify.app/api/strava-token-exchange";
    private static final String PREFS_NAME = "strava_prefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_EXPIRES_AT = "expires_at";

    private Context context;
    private SharedPreferences prefs;

    public interface StravaCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    public static class StravaRoute {
        public String id;
        public String name;
        public String description;
        public double distance;
        public double elevationGain;
        public String type;
        public boolean isPrivate;
        public String createdAt;
        public String updatedAt;

        public StravaRoute(JSONObject json) throws Exception {
            this.id = String.valueOf(json.getLong("id"));
            this.name = json.optString("name", "Unnamed Route");
            this.description = json.optString("description", "");
            this.distance = json.optDouble("distance", 0);
            this.elevationGain = json.optDouble("elevation_gain", 0);
            this.type = json.optString("type", "");
            this.isPrivate = json.optBoolean("private", true);
            this.createdAt = json.optString("created_at", "");
            this.updatedAt = json.optString("updated_at", "");
        }

        @Override
        public String toString() {
            return name + " (" + String.format("%.1f km", distance / 1000) +
                    (isPrivate ? " - Private" : " - Public") + ")";
        }
    }

    public StravaApiManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isAuthenticated() {
        String token = prefs.getString(KEY_ACCESS_TOKEN, null);
        long expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0);
        boolean isValid = token != null && System.currentTimeMillis() < (expiresAt * 1000);

        Log.d(TAG, "Token check: token=" + (token != null ? "present" : "null") +
                ", expires=" + expiresAt + ", current=" + (System.currentTimeMillis()/1000) +
                ", valid=" + isValid);

        return isValid;
    }

    public Intent createAuthIntent() {
        String scope = "read,activity:read";
        String authUrl = AUTH_URL +
                "?client_id=" + CLIENT_ID +
                "&redirect_uri=" + Uri.encode(REDIRECT_URI) +
                "&response_type=code" +
                "&approval_prompt=force" +
                "&scope=" + scope;

        Log.d(TAG, "Creating auth intent with URL: " + authUrl);
        return new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl));
    }

    public void handleAuthCallback(Uri uri, StravaCallback<Boolean> callback) {
        Log.d(TAG, "Handling auth callback: " + uri.toString());

        String code = uri.getQueryParameter("code");
        String error = uri.getQueryParameter("error");

        if (error != null) {
            Log.e(TAG, "Authorization error: " + error);
            callback.onError("Authorization denied: " + error);
            return;
        }

        if (code == null) {
            Log.e(TAG, "No authorization code received");
            callback.onError("No authorization code received");
            return;
        }

        Log.d(TAG, "Received authorization code, exchanging for token via backend");
        exchangeCodeForToken(code, callback);
    }

    private void exchangeCodeForToken(String code, StravaCallback<Boolean> callback) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Exchanging code for token via backend");

                URL url = new URL(BACKEND_TOKEN_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);

                JSONObject requestBody = new JSONObject();
                requestBody.put("code", code);
                requestBody.put("grant_type", "authorization_code");

                OutputStream os = conn.getOutputStream();
                os.write(requestBody.toString().getBytes("UTF-8"));
                os.close();

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Backend token exchange response code: " + responseCode);

                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    Log.d(TAG, "Token response received from backend");

                    JSONObject tokenResponse = new JSONObject(response.toString());

                    String accessToken = tokenResponse.getString("access_token");
                    String refreshToken = tokenResponse.getString("refresh_token");
                    long expiresAt = tokenResponse.getLong("expires_at");

                    prefs.edit()
                            .putString(KEY_ACCESS_TOKEN, accessToken)
                            .putString(KEY_REFRESH_TOKEN, refreshToken)
                            .putLong(KEY_EXPIRES_AT, expiresAt)
                            .apply();

                    Log.d(TAG, "Token exchange successful, expires at: " + expiresAt);
                    callback.onSuccess(true);
                } else {
                    String errorResponse = readErrorStream(conn);
                    Log.e(TAG, "Backend token exchange failed: " + responseCode + " - " + errorResponse);
                    callback.onError("Failed to exchange token: " + errorResponse);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error exchanging token", e);
                callback.onError("Error exchanging token: " + e.getMessage());
            }
        }).start();
    }

    private void refreshToken(StravaCallback<Boolean> callback) {
        String refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null);
        if (refreshToken == null) {
            Log.e(TAG, "No refresh token available");
            callback.onError("No refresh token available");
            return;
        }

        Log.d(TAG, "Refreshing access token via backend");

        new Thread(() -> {
            try {
                URL url = new URL(BACKEND_TOKEN_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);

                JSONObject requestBody = new JSONObject();
                requestBody.put("grant_type", "refresh_token");
                requestBody.put("refresh_token", refreshToken);

                OutputStream os = conn.getOutputStream();
                os.write(requestBody.toString().getBytes("UTF-8"));
                os.close();

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Backend token refresh response code: " + responseCode);

                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject tokenResponse = new JSONObject(response.toString());

                    String accessToken = tokenResponse.getString("access_token");
                    String newRefreshToken = tokenResponse.getString("refresh_token");
                    long expiresAt = tokenResponse.getLong("expires_at");

                    prefs.edit()
                            .putString(KEY_ACCESS_TOKEN, accessToken)
                            .putString(KEY_REFRESH_TOKEN, newRefreshToken)
                            .putLong(KEY_EXPIRES_AT, expiresAt)
                            .apply();

                    Log.d(TAG, "Token refresh successful");
                    callback.onSuccess(true);
                } else {
                    String errorResponse = readErrorStream(conn);
                    Log.e(TAG, "Backend token refresh failed: " + responseCode + " - " + errorResponse);

                    logout();
                    callback.onError("Session expired - please login again");
                }

            } catch (Exception e) {
                Log.e(TAG, "Error refreshing token", e);
                callback.onError("Error refreshing token: " + e.getMessage());
            }
        }).start();
    }

    public void fetchUserRoutes(StravaCallback<List<StravaRoute>> callback) {
        ensureValidToken(new StravaCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                makeApiCall("/athlete/routes?per_page=50", new StravaCallback<String>() {
                    @Override
                    public void onSuccess(String response) {
                        try {
                            Log.d(TAG, "Routes response: " + response);

                            JSONArray routes = new JSONArray(response);
                            List<StravaRoute> routeList = new ArrayList<>();

                            for (int i = 0; i < routes.length(); i++) {
                                try {
                                    JSONObject routeJson = routes.getJSONObject(i);
                                    routeList.add(new StravaRoute(routeJson));
                                } catch (Exception e) {
                                    Log.w(TAG, "Error parsing route at index " + i, e);
                                }
                            }

                            Log.d(TAG, "Successfully parsed " + routeList.size() + " routes");
                            callback.onSuccess(routeList);
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing routes response", e);
                            callback.onError("Error parsing routes: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onError(String error) {
                        callback.onError(error);
                    }
                });
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void downloadRouteGpx(String routeId, StravaCallback<String> callback) {
        ensureValidToken(new StravaCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                Log.d(TAG, "Downloading GPX for route: " + routeId);
                makeApiCall("/routes/" + routeId + "/export_gpx", callback);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    private void ensureValidToken(StravaCallback<Boolean> callback) {
        if (isAuthenticated()) {
            callback.onSuccess(true);
        } else {
            Log.d(TAG, "Token expired, attempting refresh");
            refreshToken(callback);
        }
    }

    private void makeApiCall(String endpoint, StravaCallback<String> callback) {
        new Thread(() -> {
            try {
                String accessToken = prefs.getString(KEY_ACCESS_TOKEN, null);
                if (accessToken == null) {
                    callback.onError("No access token available");
                    return;
                }

                String fullUrl = BASE_URL + endpoint;
                Log.d(TAG, "Making API call to: " + fullUrl);

                URL url = new URL(fullUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                conn.setRequestProperty("Accept", "application/json, text/plain, */*");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "API response code: " + responseCode);

                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                        if (endpoint.contains("export_gpx")) {
                            response.append("\n");
                        }
                    }
                    reader.close();

                    String result = response.toString();
                    Log.d(TAG, "API call successful, response length: " + result.length());

                    if (result.length() > 200) {
                        Log.d(TAG, "Response preview: " + result.substring(0, 200) + "...");
                    } else {
                        Log.d(TAG, "Full response: " + result);
                    }

                    callback.onSuccess(result);
                } else if (responseCode == 401) {
                    Log.w(TAG, "Unauthorized - token may be expired");
                    callback.onError("Authentication failed - please login again");
                } else if (responseCode == 404) {
                    callback.onError("Route not found or not accessible");
                } else {
                    String errorResponse = readErrorStream(conn);
                    Log.e(TAG, "API call failed: " + responseCode + " - " + errorResponse);
                    callback.onError("API call failed: " + responseCode + " - " + errorResponse);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error making API call to " + endpoint, e);
                callback.onError("Network error: " + e.getMessage());
            }
        }).start();
    }

    private String readErrorStream(HttpURLConnection conn) {
        try {
            if (conn.getErrorStream() != null) {
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorResponse.append(line);
                }
                errorReader.close();
                return errorResponse.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading error stream", e);
        }
        return "Unknown error";
    }

    public void logout() {
        Log.d(TAG, "Logging out - clearing stored tokens");
        prefs.edit().clear().apply();
    }
}