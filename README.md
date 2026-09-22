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

Requirements: JDK 17 and Android SDK 36.

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions also builds `zyplo-partner.apk` on every push and exposes it as
the `zyplo-partner-apk` workflow artifact. A checked-in test build is available
at [`downloads/zyplo-partner.apk`](downloads/zyplo-partner.apk).

## Lovable push connection

The app is connected to the push configuration currently deployed by the Zyplo
Lovable site:

- OneSignal app `9bf9df8e-1124-44aa-bc80-b0399dd26e8a`
- Firebase project `zyplonew` / sender `847532358161`
- Supabase project `itjjcscyqqxkeipkccgl`

After vendor login, the app reads the existing Supabase browser session, calls
OneSignal `login` with the Supabase user ID, and registers native FCM tokens in
the existing `push_tokens` table as `android-fcm`. This uses the public Supabase
anon key plus the signed-in user's JWT, so RLS remains in force; no service-role
key is embedded.

## Direct Firebase configuration

Create an Android Firebase app with package name `in.zyplo.vendor`, enable Cloud
Messaging, and copy its Android `mobilesdk_app_id` into
`~/.gradle/gradle.properties` or a GitHub Actions variable:

```properties
FIREBASE_APPLICATION_ID=1:1234567890:android:abc123
```

The deployed public API key, project ID, and sender ID are already configured.
An Android Firebase App ID cannot be derived from the site's Web App ID; Firebase
requires the Android-specific value. Never put a Firebase service-account key in
this repository or APK.

The app registers generated tokens directly in Lovable's existing Supabase
`push_tokens` table and also dispatches this browser event after every page load:

```js
window.addEventListener("zyplo:fcm-token", event => {
  // Native token already saved to the signed-in user's push_tokens row.
});
```

## Required backend API

All API calls include the vendor's WebView session cookie and JSON content type.
The default endpoints can be changed with Gradle properties:

```properties
ZYPLO_BASE_URL=https://zyplo.in/grocery-vendor-login
ZYPLO_ALLOWED_HOST=zyplo.in
ZYPLO_LOCATION_ENDPOINT=https://zyplo.in/api/vendor/location
```

Expected requests:

```text
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

The location endpoint remains an integration default because the public website
does not publish a vendor-location API specification.

## Permissions

On first launch the app asks for location and notification permission, then
offers the Android “display over other apps” setting for the floating bubble.
Location runs as a visible foreground service so Android continues delivering
updates while the vendor uses other apps. The app intentionally does not request
unrestricted background-location permission.
