# TODO

## Blocking before first real use

- [ ] **Compile the app** in Android Studio (not yet built anywhere; fix any compile errors)
- [ ] **Commit** both repos (backend changes in `happy-gate-food-ordering`, initial commit here)
- [ ] **Deploy backend** on Vercel with Neon Postgres; set env vars (`KITCHEN_TOKEN`, Resend, `RESTAURANT_TIMEZONE=Europe/Amsterdam`) and run the one-off menu seed endpoint
- [ ] **Firebase**: create project, add Android app `nl.happygate.orders`, put `google-services.json` in `app/`,
      set `FIREBASE_CREDENTIALS` (service-account JSON) on Vercel
- [ ] **Printer**: confirm exact Epson model and that it is LAN/Wi‑Fi; give it a fixed IP on the router;
      run Settings → *Test print* from the phone
- [ ] **Phone setup**: allow notifications, "New orders" channel sound on, battery optimisation off,
      consider Do Not Disturb exception
- [ ] End-to-end test: order on the website → phone rings → accept 30 min → website shows time →
      ticket prints → mark ready → website shows ready

## Hardening (plan phase 5)

- [ ] Test with screen locked, app swiped away, weak Wi‑Fi, printer off, paper out, printer power-cycled
- [ ] Decide the 3‑minute auto-reject window: keep, lengthen, or disable when a push was delivered
- [ ] Add a visible "last synced hh:mm" and a manual pull-to-refresh
- [ ] Persist "already alerted" order ids so a restart does not re-ring for old pending orders
- [ ] Rotate `KITCHEN_TOKEN` procedure; consider per-device tokens so one phone can be revoked
- [ ] Backend: remove stale `device_tokens` not seen for 60+ days
- [ ] Backend: order total currency symbol in `printer.py`/`email.py` is `$`; should be `€`
- [ ] Backend: `GET /api/orders/{id}` returns a tuple on 404 instead of a real 404 response (pre-existing)
- [ ] Monitoring: alert if push sending fails repeatedly (Vercel function logs or email)

## Printing improvements

- [ ] Swap in Epson ePOS SDK behind `PrinterTransport` if the model needs Bluetooth/USB or paper-out status
- [ ] 58 mm paper option (32 columns) in Settings
- [ ] Optional second copy (customer receipt) and auto-reprint on "Retry" from the notification

## Nice to have

- [ ] Dutch/Chinese UI strings (currently English)
- [ ] Sound picker for the alert; repeat the alarm until a pending order is handled
- [ ] Daily summary in History (count, revenue)
- [ ] Delivery orders (backend currently pickup-only: `service_delivery=False`)
- [ ] SMS to customer on ready (Resend email only today)
- [ ] Unit tests for `EscPosTicket` layout and `OrdersViewModel` actions
