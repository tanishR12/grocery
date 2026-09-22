package in.zyplo.vendor;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class OrderActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getStringExtra("action");
        String orderId = intent.getStringExtra("order_id");
        String endpoint = intent.getStringExtra("endpoint");
        if (action == null || orderId == null || endpoint == null) return;

        context.stopService(new Intent(context, OrderAlertService.class));
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.cancel(OrderAlertService.notificationId(orderId));

        PendingResult pending = goAsync();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            boolean success = false;
            try {
                NetworkClient.post(
                        endpoint,
                        new JSONObject().put("order_id", orderId).put("action", action)
                );
                success = true;
            } catch (Exception ignored) {
            } finally {
                boolean result = success;
                new android.os.Handler(context.getMainLooper()).post(() -> Toast.makeText(
                        context,
                        result ? "Order " + action + "ed" : "Could not " + action + " order",
                        Toast.LENGTH_LONG
                ).show());
                executor.shutdown();
                pending.finish();
            }
        });
    }
}
