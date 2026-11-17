# Happy Gate Orders — restaurant Android app

Companion app for the customer-ordering website/backend in the separate
`happy-gate-food-ordering` repository.

Native Kotlin / Jetpack Compose app for the owner's phone. Customers keep ordering
on the website (the `happy-gate-food-ordering` repo); this app receives the order, lets staff accept it with a ready time
(15 / 30 / 45 min or custom) or reject it with a reason, prints a kitchen ticket on
an Epson network printer, and marks the order ready / picked up. The backend in this
backend repo is the single source of truth; the app never trusts a push notification as the
order itself.

Implements phases 1–4 of `restaurant_android_order_app_plan.md` (phase 5 hardening
is a checklist to work through with the real printer and phone).

## Backend side (in `happy-gate-food-ordering`)

Authenticated API under `/api/restaurant/*` (`Authorization: Bearer <KITCHEN_TOKEN>`):

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/restaurant/ping` | Login check, returns restaurant name |
| GET | `/api/restaurant/orders?scope=pending\|active\|history&date=&q=` | Lists with full items/modifiers |
| GET | `/api/restaurant/orders/{id}` | One order incl. print history |
| POST | `/api/restaurant/orders/{id}/accept` `{preparation_minutes}` | pending → approved, server computes `estimated_ready_at` |
| POST | `/api/restaurant/orders/{id}/reject` `{reason}` | pending → rejected_manual |
| POST | `/api/restaurant/orders/{id}/ready` | approved → ready |
| POST | `/api/restaurant/orders/{id}/complete` | ready → completed |
| POST | `/api/restaurant/orders/{id}/print-events` `{status,message,device}` | Audit of print attempts |
| POST | `/api/restaurant/devices/register` `{token}` | FCM token of the phone |

All transitions are enforced server-side and idempotent (repeating a successful
accept/reject/ready returns 200 with the current order; a conflicting transition is 409).
The legacy web kitchen page (`/kitchen?token=…`) keeps working unchanged.

Backend env vars (see that repo's `.env.example`): `KITCHEN_TOKEN` (the app's login token),
`RESTAURANT_TIMEZONE` (default `Europe/Amsterdam`), `FIREBASE_CREDENTIALS`
(service-account JSON path or inline JSON; empty = push disabled).

Backend tests live in that repo (`python -m pytest tests`).

## Building the app

1. Open this folder in Android Studio (Ladybug or newer, JDK 17) and let it sync,
   or run `./gradlew assembleDebug` with `ANDROID_HOME` set.
2. Install `app/build/outputs/apk/debug/app-debug.apk` on the phone (Android 8.0+).

Push notifications (optional but recommended):

1. Create a Firebase project, add an Android app with package `nl.happygate.orders`,
   download `google-services.json` into `app/`. The Gradle build picks it up
   automatically; without it the app builds and polls while open, but gets no background alerts.
2. In Firebase → Project settings → Service accounts, generate a private key and set
   `FIREBASE_CREDENTIALS` on the backend (Render: paste the JSON as the env value).

## Using the app

- **Connect:** server URL (the Render URL) and the `KITCHEN_TOKEN`. Allow notifications when asked.
- **New:** every pending order with its 3‑minute countdown. Tap *Accept* → pick 15/30/45 or custom
  minutes → the customer page/email shows the confirmed time. *Reject* asks for a reason.
- **Active:** accepted orders sorted by ready time, late ones highlighted. *Mark ready*, then *Picked up*.
  Print state is shown separately (queued / printing / printed / FAILED / uncertain) with *Reprint*.
- **History:** search by order number or date; shows rejection reason and print history.
- **Settings:** alert sound toggle, printer IP/port, enable auto-print on accept, *Test print*, log out.

While the app is open it polls every 5 s and rings (alarm stream + vibration) for unseen orders.
In the background FCM shows a high-priority notification on the `new_orders` channel; tapping it
opens the order. On the phone, turn off battery optimisation for the app and keep the
"New orders" channel sound on.

## Printing

Tickets are raw ESC/POS over TCP port 9100 (what the backend's `app/services/printer.py` already uses), which
every Epson TM LAN/Wi‑Fi model understands, so no model-specific SDK is required to start.
Phone and printer must be on the same Wi‑Fi. Printing happens on the phone after *Accept* so the
ticket carries the confirmed ready time; the backend does not print for app-accepted orders.

The transport is behind the `PrinterTransport` interface (`printing/PrinterTransport.kt`), so the
Epson ePOS SDK (needed for Bluetooth/USB models or paper-out status) can be dropped in once the
exact model is known. Outcomes: *Sent* (all bytes delivered) → shown as **Printed**; *Failed*
(nothing reached the printer) → **Print FAILED** with *Retry*; *Uncertain* (connection dropped
mid-ticket) → **Print uncertain**, staff decide whether to reprint. Duplicate accept taps or pushes
never queue a second ticket; only an explicit *Reprint* does.

## Not done / needs the real hardware

- The Android project has not been compiled in this environment (no Android SDK available);
  expect to fix at most minor compile issues on first sync.
- Epson model/interface confirmation and paper-out / power-cycle testing (plan §8 step 7).
- Firebase project + `google-services.json` (per-project secret, not committed).
- Multi-restaurant / per-staff accounts: the app shares the single `KITCHEN_TOKEN`.
