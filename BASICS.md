# Basics — how the Happy Gate ordering system works

Two projects, one restaurant:

| Project | Folder | What it is |
| --- | --- | --- |
| Website + backend | `happy-gate-food-ordering` | Python/FastAPI site where customers order; Neon Postgres database; deployed on Vercel |
| Restaurant app | `happy-gate-orders-android` (this repo) | Kotlin/Jetpack Compose app on the owner's Android phone |

## The flow of one order

```
Customer phone/laptop          Backend (Vercel)              Owner's Android phone        Epson printer
---------------------          ----------------              ---------------------        -------------
1. Picks dishes, checks out -> 2. Saves order as PENDING
                               3. Sends FCM push --------->  4. Phone rings, shows order
                                                             5. Owner taps Accept + 30 min
                               6. Sets APPROVED,          <-
                                  ready time = now + 30
7. Status page shows                                          8. App prints ticket -------> paper ticket
   "Confirmed, pickup 18:45"                                     with ready time
                                                             9. Owner taps "Mark ready"
10. Page shows "Order Ready"   <- READY
                                                             11. Owner taps "Picked up" -> COMPLETED
```

If the owner does nothing for 3 minutes the backend auto-rejects the order (`rejected_timeout`).
Reject with a reason gives `rejected_manual`. The customer is emailed on approve and reject
(when `RESEND_API_KEY` is set on the backend).

## Order states

```
pending_restaurant_confirmation -> approved -> ready -> completed
              |
              +-> rejected_manual | rejected_timeout
```

The backend owns these states. The app only asks for transitions; the backend refuses invalid ones
(409) and treats repeats as no-ops, so a double tap or a retried request can never apply twice.

## Print states (separate from order states)

```
NOT_QUEUED -> QUEUED -> SENDING -> PRINTED
                          |
                          +-> FAILED (nothing reached the printer, retry is safe)
                          +-> UNCERTAIN (connection dropped mid-ticket, check the paper)
```

A failed print never changes the order: the customer is still confirmed. Staff see the failure
in the app and tap *Reprint*. Every attempt is stored on the backend as a print event.

## How the phone learns about new orders

1. **Push (background):** backend sends a Firebase Cloud Messaging message to every registered phone.
   The app shows a loud notification; tapping opens the order. Needs `google-services.json` in `app/`
   and `FIREBASE_CREDENTIALS` on the backend.
2. **Polling (foreground):** while the app is open it fetches pending + active orders every 5 seconds
   and rings for orders it has not alerted about yet.
3. **Reconciliation:** on launch, resume and reconnect it refetches everything, so nothing is lost if a
   push is late or the app was killed.

A push is only a hint. The app always loads the order from the backend.

## Authentication

One shared secret, `KITCHEN_TOKEN`, set on the backend. The app sends it as
`Authorization: Bearer <token>`; the old web kitchen page sends it as `?token=`.
Anyone with the token can accept/reject orders, so keep it private and rotate it if a phone is lost.

## Printing

The app talks ESC/POS directly to the printer over Wi‑Fi on TCP port 9100 (phone and printer on the
same network). Ticket contents: order number, ready time, order/accept times, customer, phone, items
with modifiers and notes, total, order notes. 48 columns (80 mm paper) by default.
Settings → *Test print* checks the connection without an order.

## Where things live

Backend repo:

- `app/routers/restaurant.py` — the API the app uses
- `app/routers/orders.py`, `kitchen.py` — customer ordering and the legacy web kitchen page
- `app/services/push.py` — FCM sending; `email.py` — customer emails; `printer.py` — server-side printing (legacy)
- `app/models.py` — tables (`orders`, `order_items`, `print_events`, `device_tokens`, menu)
- `tests/` — API tests (`python -m pytest tests`)

This repo:

- `app/src/main/java/nl/happygate/orders/data/` — API client, models, settings (DataStore), repository
- `.../ui/` — screens: Login, Pending, Active, History, OrderDetail, Settings, dialogs
- `.../printing/` — ESC/POS ticket builder, TCP transport, print queue
- `.../notifications/` — notification channel, in-app alarm, FCM service
- `restaurant_android_order_app_plan.md` — the original plan

## Daily use (owner)

1. Keep the app installed, notifications allowed, battery optimisation off for the app.
2. New order rings → open → **Accept** with a time, or **Reject**.
3. Ticket prints. When the food is bagged, **Mark ready**; when collected, **Picked up**.
4. Red "Print FAILED" → check printer power/Wi‑Fi → **Retry print**.
5. Red "Offline" bar at the top → phone has no internet; orders shown may be stale.
