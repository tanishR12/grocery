package in.zyplo.vendor;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.onesignal.OneSignal;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends AppCompatActivity {
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private GeolocationPermissions.Callback geolocationCallback;
    private String geolocationOrigin;

    private final ActivityResultLauncher<String[]> permissions = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    startLocationTracking();
                    if (geolocationCallback != null) {
                        geolocationCallback.invoke(geolocationOrigin, true, false);
                    }
                } else if (geolocationCallback != null) {
                    geolocationCallback.invoke(geolocationOrigin, false, false);
                }
                geolocationCallback = null;
                geolocationOrigin = null;
            }
    );

    private final ActivityResultLauncher<Intent> filePicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(
                            result.getResultCode(), result.getData()));
                    fileCallback = null;
                }
            }
    );

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setGeolocationEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(true);
        webView.addJavascriptInterface(new AndroidBridge(), "ZyploAndroid");
        webView.setWebViewClient(new TrustedWebViewClient());
        webView.setWebChromeClient(new VendorChromeClient());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack();
                else finish();
            }
        });

        requestRuntimePermissions();
        loadIntent(getIntent());
        connectLovablePush(null);
        sendFcmTokenToWebsite();
        webView.postDelayed(this::offerFloatingBubble, 1_500);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        loadIntent(intent);
        stopService(new Intent(this, OrderAlertService.class));
    }

    private void loadIntent(Intent intent) {
        String requested = intent.getStringExtra("order_url");
        Uri deepLink = intent.getData();
        if (requested != null && isTrusted(Uri.parse(requested))) webView.loadUrl(requested);
        else if (deepLink != null && isTrusted(deepLink)) webView.loadUrl(deepLink.toString());
        else if (webView.getUrl() == null) webView.loadUrl(BuildConfig.BASE_URL);
    }

    private void requestRuntimePermissions() {
        List<String> needed = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!needed.isEmpty()) permissions.launch(needed.toArray(new String[0]));
        else startLocationTracking();
    }

    private void startLocationTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            ContextCompat.startForegroundService(this, new Intent(this, LocationTrackingService.class));
        }
    }

    private void sendFcmTokenToWebsite() {
        if (FirebaseApp.getApps(this).isEmpty()) return;
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            evaluateEvent("zyplo:fcm-token", token);
            connectLovablePush(token);
        });
    }

    private void connectLovablePush(String fcmToken) {
        String token = fcmToken == null ? "" : fcmToken;
        String script = "(()=>{"
                + "const token=" + JSONObject.quote(token) + ";"
                + "if(token)window.__zyploAndroidFcmToken=token;"
                + "const findSession=()=>{try{"
                + "let raw=localStorage.getItem('sb-itjjcscyqqxkeipkccgl-auth-token');"
                + "if(!raw){for(let i=0;i<localStorage.length;i++){const k=localStorage.key(i);"
                + "if(k&&k.startsWith('sb-')&&k.endsWith('-auth-token')){raw=localStorage.getItem(k);break;}}}"
                + "if(!raw)return false;const s=JSON.parse(raw);"
                + "const access=s.access_token||(s.currentSession&&s.currentSession.access_token);"
                + "const user=s.user||(s.currentSession&&s.currentSession.user);"
                + "if(!user||!user.id)return false;"
                + "window.ZyploAndroid.setVendorIdentity(String(user.id));"
                + "const nativeToken=window.__zyploAndroidFcmToken||'';"
                + "if(nativeToken&&access){fetch(" + JSONObject.quote(BuildConfig.SUPABASE_URL + "/rest/v1/push_tokens?on_conflict=user_id,token") + ",{"
                + "method:'POST',headers:{'apikey':" + JSONObject.quote(BuildConfig.SUPABASE_ANON_KEY)
                + ",'Authorization':'Bearer '+access,'Content-Type':'application/json',"
                + "'Prefer':'resolution=merge-duplicates'},body:JSON.stringify({user_id:user.id,"
                + "token:nativeToken,device_type:'android-fcm',is_active:true,last_used_at:new Date().toISOString()})"
                + "}).catch(()=>{});}return true;}catch(e){return false;}};"
                + "if(findSession())return;if(window.__zyploAndroidPushTimer)return;"
                + "let attempts=0;window.__zyploAndroidPushTimer=setInterval(()=>{"
                + "if(findSession()||++attempts>=60){clearInterval(window.__zyploAndroidPushTimer);"
                + "window.__zyploAndroidPushTimer=null;}},2000);})();";
        webView.post(() -> webView.evaluateJavascript(script, null));
    }

    private void offerFloatingBubble() {
        if (Settings.canDrawOverlays(this)
                || getPreferences(MODE_PRIVATE).getBoolean("overlay_prompted", false)) return;
        new AlertDialog.Builder(this)
                .setTitle("Enable order bubble")
                .setMessage("Allow Zyplo Vendor to show new orders over other apps.")
                .setPositiveButton("Enable", (dialog, which) -> {
                    getPreferences(MODE_PRIVATE).edit().putBoolean("overlay_prompted", true).apply();
                    startActivity(new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())
                    ));
                })
                .setNegativeButton("Not now", (dialog, which) ->
                        getPreferences(MODE_PRIVATE).edit()
                                .putBoolean("overlay_prompted", true).apply())
                .show();
    }

    private void evaluateEvent(String event, String detail) {
        if (webView == null) return;
        String script = "window.dispatchEvent(new CustomEvent("
                + JSONObject.quote(event) + ",{detail:" + JSONObject.quote(detail) + "}));";
        webView.post(() -> webView.evaluateJavascript(script, null));
    }

    private boolean isTrusted(Uri uri) {
        String host = uri.getHost();
        return "https".equalsIgnoreCase(uri.getScheme())
                && host != null
                && (host.equals(BuildConfig.ALLOWED_HOST)
                || host.endsWith("." + BuildConfig.ALLOWED_HOST));
    }

    public final class AndroidBridge {
        @JavascriptInterface
        public void enableFloatingOrderBubble() {
            runOnUiThread(() -> {
                if (!Settings.canDrawOverlays(MainActivity.this)) {
                    Intent intent = new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())
                    );
                    startActivity(intent);
                }
            });
        }

        @JavascriptInterface
        public void startLocationTracking() {
            runOnUiThread(MainActivity.this::requestRuntimePermissions);
        }

        @JavascriptInterface
        public void stopOrderAlarm() {
            stopService(new Intent(MainActivity.this, OrderAlertService.class));
        }

        @JavascriptInterface
        public void setVendorIdentity(String userId) {
            if (userId == null || !userId.matches("[0-9a-fA-F-]{36}")) return;
            OneSignal.login(userId);
            OneSignal.getUser().addTag("role", "grocery_vendor");
        }
    }

    private final class TrustedWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (isTrusted(uri)) return false;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception ignored) {
                Toast.makeText(MainActivity.this, "Cannot open this link", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            connectLovablePush(null);
            sendFcmTokenToWebsite();
        }
    }

    private final class VendorChromeClient extends WebChromeClient {
        @Override
        public void onGeolocationPermissionsShowPrompt(
                String origin,
                GeolocationPermissions.Callback callback
        ) {
            Uri uri = Uri.parse(origin);
            if (!isTrusted(uri)) {
                callback.invoke(origin, false, false);
                return;
            }
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                callback.invoke(origin, true, false);
            } else {
                geolocationOrigin = origin;
                geolocationCallback = callback;
                requestRuntimePermissions();
            }
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            runOnUiThread(() -> request.deny());
        }

        @Override
        public boolean onShowFileChooser(
                WebView view,
                ValueCallback<Uri[]> callback,
                FileChooserParams params
        ) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;
            try {
                filePicker.launch(params.createIntent());
            } catch (Exception e) {
                fileCallback = null;
                Toast.makeText(MainActivity.this, "No file picker available", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
    }
}
