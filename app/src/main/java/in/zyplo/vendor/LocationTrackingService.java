package in.zyplo.vendor;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.IBinder;
import android.webkit.CookieManager;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LocationTrackingService extends Service {
    private static final int NOTIFICATION_ID = 2001;
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private FusedLocationProviderClient locationClient;

    private final LocationCallback callback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult result) {
            Location location = result.getLastLocation();
            if (location != null) upload(location);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        locationClient = LocationServices.getFusedLocationProviderClient(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, notification());
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return START_NOT_STICKY;
        }
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30_000)
                .setMinUpdateIntervalMillis(15_000)
                .setMinUpdateDistanceMeters(20)
                .build();
        locationClient.removeLocationUpdates(callback);
        locationClient.requestLocationUpdates(request, callback, getMainLooper());
        return START_STICKY;
    }

    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        return new NotificationCompat.Builder(this, VendorApplication.LOCATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_store)
                .setContentTitle("Zyplo Vendor is online")
                .setContentText("Sharing location for order operations")
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    private void upload(Location location) {
        networkExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL endpoint = new URL(BuildConfig.LOCATION_ENDPOINT);
                if (!"https".equalsIgnoreCase(endpoint.getProtocol())
                        || !isAllowedHost(endpoint.getHost())) return;
                JSONObject body = new JSONObject()
                        .put("latitude", location.getLatitude())
                        .put("longitude", location.getLongitude())
                        .put("accuracy", location.getAccuracy())
                        .put("recorded_at", location.getTime());
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);

                connection = (HttpURLConnection) endpoint.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(10_000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json");
                String cookies = CookieManager.getInstance().getCookie(BuildConfig.BASE_URL);
                if (cookies != null && !cookies.isEmpty()) {
                    connection.setRequestProperty("Cookie", cookies);
                }
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
                connection.getResponseCode();
            } catch (Exception ignored) {
                // The next scheduled location update retries automatically.
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private boolean isAllowedHost(String host) {
        return host.equals(BuildConfig.ALLOWED_HOST)
                || host.endsWith("." + BuildConfig.ALLOWED_HOST);
    }

    @Override
    public void onDestroy() {
        if (locationClient != null) locationClient.removeLocationUpdates(callback);
        networkExecutor.shutdownNow();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
