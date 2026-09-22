package in.zyplo.vendor;

import android.content.Intent;

import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public final class VendorMessagingService extends FirebaseMessagingService {
    @Override
    public void onMessageReceived(RemoteMessage message) {
        Map<String, String> data = message.getData();
        String orderId = data.get("order_id");
        if (orderId == null || orderId.isEmpty()) return;

        RemoteMessage.Notification notification = message.getNotification();
        Intent alert = new Intent(this, OrderAlertService.class)
                .putExtra("order_id", orderId)
                .putExtra("title", value(data.get("title"),
                        notification == null ? null : notification.getTitle(), "New order"))
                .putExtra("body", value(data.get("body"),
                        notification == null ? null : notification.getBody(), "Tap to review"))
                .putExtra("order_url", value(data.get("order_url"), BuildConfig.BASE_URL))
                .putExtra("accept_url", data.get("accept_url"))
                .putExtra("reject_url", data.get("reject_url"));
        try {
            ContextCompat.startForegroundService(this, alert);
        } catch (Exception ignored) {
            // Android still displays server notification payloads when background start is restricted.
        }
    }

    @Override
    public void onNewToken(String token) {
        getSharedPreferences("push", MODE_PRIVATE)
                .edit()
                .putString("last_fcm_token", token)
                .apply();
    }

    private static String value(String first, String fallback) {
        return value(first, fallback, "");
    }

    private static String value(String first, String second, String fallback) {
        if (first != null && !first.isEmpty()) return first;
        if (second != null && !second.isEmpty()) return second;
        return fallback;
    }
}
