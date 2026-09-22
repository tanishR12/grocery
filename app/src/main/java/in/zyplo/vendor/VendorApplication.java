package in.zyplo.vendor;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.media.AudioAttributes;
import android.provider.Settings;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

public final class VendorApplication extends Application {
    public static final String ORDER_CHANNEL = "orders";
    public static final String LOCATION_CHANNEL = "location";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
        initializeFirebase();
    }

    private void initializeFirebase() {
        if (!FirebaseApp.getApps(this).isEmpty() || BuildConfig.FIREBASE_APPLICATION_ID.isEmpty()) {
            return;
        }
        FirebaseOptions options = new FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                .build();
        FirebaseApp.initializeApp(this, options);
    }

    private void createNotificationChannels() {
        NotificationManager manager = getSystemService(NotificationManager.class);

        AudioAttributes audio = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build();
        NotificationChannel orders = new NotificationChannel(
                ORDER_CHANNEL,
                getString(R.string.order_channel),
                NotificationManager.IMPORTANCE_HIGH
        );
        orders.enableVibration(true);
        orders.setVibrationPattern(new long[]{0, 500, 250, 500, 250, 900});
        orders.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, audio);
        manager.createNotificationChannel(orders);

        NotificationChannel location = new NotificationChannel(
                LOCATION_CHANNEL,
                getString(R.string.location_channel),
                NotificationManager.IMPORTANCE_LOW
        );
        location.setSound(null, null);
        manager.createNotificationChannel(location);
    }
}
