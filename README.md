# Zyplo Vendor Android

Native Android shell for the Zyplo grocery vendor website. It keeps the existing
website as the source of truth while adding Android capabilities:

- persistent vendor login and full website navigation
- foreground live-location updates
- Firebase Cloud Messaging order notifications
- looping order siren, vibration, and draggable floating order bubble
- notification actions to accept or reject an order
- file uploads and website geolocation

## Build

Requirements: JDK 17 and Android SDK 35.

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Firebase configuration

Create an Android Firebase app with package name `in.zyplo.vendor`, enable Cloud
Messaging, then provide the public Firebase Android identifiers in
`~/.gradle/gradle.properties` or CI secrets:

```properties
FIREBASE_APPLICATION_ID=1:1234567890:android:abc123
FIREBASE_API_KEY=...
FIREBASE_PROJECT_ID=...
FIREBASE_SENDER_ID=1234567890
```

These identifiers configure the Firebase client; a Firebase service-account key
must remain only on the server. The app sends new/rotated tokens to
`https://zyplo.in/api/vendor/device-token`, and also dispatches this browser event
after every page load:

```js
window.addEventListener("zyplo:fcm-token", event => {
  // POST event.detail to the signed-in vendor's device-token endpoint.
});
```

## Required backend API

All API calls include the vendor's WebView session cookie and JSON content type.
The default endpoints can be changed with Gradle properties:

```properties
ZYPLO_BASE_URL=https://zyplo.in/grocery-vendor-login
ZYPLO_ALLOWED_HOST=zyplo.in
ZYPLO_LOCATION_ENDPOINT=https://zyplo.in/api/vendor/location
ZYPLO_DEVICE_TOKEN_ENDPOINT=https://zyplo.in/api/vendor/device-token
```

Expected requests:

```text
POST /api/vendor/device-token
{"token":"FCM_TOKEN","platform":"android"}

POST /api/vendor/location
{"latitude":17.0,"longitude":78.0,"accuracy":8.5,"recorded_at":1780000000000}
```

The server should send **high-priority data messages**, not notification-only
messages, to guarantee that the app receives the order data:

```json
{
  "message": {
    "token": "FCM_TOKEN",
    "android": {"priority": "high", "ttl": "300s"},
    "data": {
      "order_id": "ORD-123",
      "title": "New grocery order",
      "body": "₹540 • 8 items",
      "order_url": "https://zyplo.in/grocery-vendor/orders/ORD-123",
      "accept_url": "https://zyplo.in/api/vendor/orders/ORD-123/accept",
      "reject_url": "https://zyplo.in/api/vendor/orders/ORD-123/reject"
    }
  }
}
```

Accept/reject sends `{"order_id":"ORD-123","action":"accept"}` (or `reject`) to
the corresponding URL. For safety, the app refuses non-HTTPS endpoints and any
host outside `zyplo.in`.

The location and token endpoint paths are integration defaults because the
public website does not publish an API specification. They must exist on the
Zyplo backend for live tracking and push registration to work.

## Permissions

On first launch the app asks for location and notification permission, then
offers the Android “display over other apps” setting for the floating bubble.
Location runs as a visible foreground service so Android continues delivering
updates while the vendor uses other apps. The app intentionally does not request
unrestricted background-location permission.
