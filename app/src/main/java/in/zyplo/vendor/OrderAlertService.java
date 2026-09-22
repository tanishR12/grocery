package in.zyplo.vendor;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public final class OrderAlertService extends Service {
    private MediaPlayer player;
    private WindowManager windowManager;
    private View bubble;
    private String orderUrl;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String orderId = value(intent.getStringExtra("order_id"), "unknown");
        String title = value(intent.getStringExtra("title"), "New order");
        String body = value(intent.getStringExtra("body"), "Tap to review");
        orderUrl = value(intent.getStringExtra("order_url"), BuildConfig.BASE_URL);
        startForeground(notificationId(orderId), notification(intent, orderId, title, body));
        startSiren();
        showBubble();
        return START_NOT_STICKY;
    }

    static int notificationId(String orderId) {
        return 10_000 + Math.abs(orderId.hashCode() % 20_000);
    }

    private Notification notification(Intent source, String orderId, String title, String body) {
        Intent openIntent = new Intent(this, MainActivity.class)
                .putExtra("order_url", orderUrl)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open = PendingIntent.getActivity(
                this,
                orderId.hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                this, VendorApplication.ORDER_CHANNEL)
                .setSmallIcon(R.drawable.ic_store)
                .setColor(Color.rgb(22, 163, 74))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVibrate(new long[]{0, 500, 250, 500, 250, 900});

        addAction(builder, orderId, "accept", "Accept", source.getStringExtra("accept_url"));
        addAction(builder, orderId, "reject", "Reject", source.getStringExtra("reject_url"));
        return builder.build();
    }

    private void addAction(
            NotificationCompat.Builder builder,
            String orderId,
            String action,
            String label,
            String endpoint
    ) {
        if (endpoint == null || endpoint.isEmpty()) return;
        Intent intent = new Intent(this, OrderActionReceiver.class)
                .putExtra("order_id", orderId)
                .putExtra("action", action)
                .putExtra("endpoint", endpoint);
        PendingIntent pending = PendingIntent.getBroadcast(
                this,
                (orderId + action).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        builder.addAction(0, label, pending);
    }

    private void startSiren() {
        if (player != null) return;
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            player.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception ignored) {
            if (player != null) player.release();
            player = null;
        }
    }

    private void showBubble() {
        if (bubble != null || !Settings.canDrawOverlays(this)) return;
        windowManager = getSystemService(WindowManager.class);
        TextView view = new TextView(this) {
            @Override
            public boolean performClick() {
                super.performClick();
                return true;
            }
        };
        view.setText("NEW\nORDER");
        view.setTextColor(Color.WHITE);
        view.setTextSize(12);
        view.setGravity(Gravity.CENTER);
        view.setElevation(12);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(Color.rgb(220, 38, 38));
        background.setStroke(4, Color.WHITE);
        view.setBackground(background);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                176,
                176,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 24;
        params.y = 240;
        view.setOnClickListener(clicked -> openOrder());
        view.setOnTouchListener(new BubbleTouchListener(params));
        windowManager.addView(view, params);
        bubble = view;
    }

    private void openOrder() {
        Intent open = new Intent(this, MainActivity.class)
                .putExtra("order_url", orderUrl)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(open);
        stopSelf();
    }

    private final class BubbleTouchListener implements View.OnTouchListener {
        private final WindowManager.LayoutParams params;
        private float downX;
        private float downY;
        private int initialX;
        private int initialY;

        BubbleTouchListener(WindowManager.LayoutParams params) {
            this.params = params;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    downY = event.getRawY();
                    initialX = params.x;
                    initialY = params.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    params.x = initialX - (int) (event.getRawX() - downX);
                    params.y = initialY + (int) (event.getRawY() - downY);
                    windowManager.updateViewLayout(view, params);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (Math.abs(event.getRawX() - downX) < 18
                            && Math.abs(event.getRawY() - downY) < 18) {
                        view.performClick();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    private static String value(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    @Override
    public void onDestroy() {
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
        if (bubble != null && windowManager != null) {
            windowManager.removeView(bubble);
            bubble = null;
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
