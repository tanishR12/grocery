package in.zyplo.vendor;

import android.webkit.CookieManager;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class NetworkClient {
    private NetworkClient() {}

    static int post(String address, JSONObject body) throws Exception {
        URL url = new URL(address);
        String host = url.getHost();
        if (!"https".equalsIgnoreCase(url.getProtocol())
                || !(host.equals(BuildConfig.ALLOWED_HOST)
                || host.endsWith("." + BuildConfig.ALLOWED_HOST))) {
            throw new SecurityException("Untrusted order action endpoint");
        }

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(12_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            String cookies = CookieManager.getInstance().getCookie(BuildConfig.BASE_URL);
            if (cookies != null && !cookies.isEmpty()) {
                connection.setRequestProperty("Cookie", cookies);
            }
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Server returned HTTP " + status);
            }
            return status;
        } finally {
            connection.disconnect();
        }
    }
}
