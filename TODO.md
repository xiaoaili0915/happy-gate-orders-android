# TODO

## Where we are (2026-11-17)

Done: backend API + tests, Android app written, both repos on GitHub (`xiaoaili0915`), Vercel project created,
Neon Postgres resource `happy-gate-db` added, Render removed from the code.

## Next steps, in order

1. [ ] **Vercel: finish the deploy** (owner)
   - Project → Settings → Environment Variables: `KITCHEN_TOKEN` (long random string), `RESEND_API_KEY`,
     `RESTAURANT_EMAIL`, `FROM_EMAIL`, `RESTAURANT_TIMEZONE=Europe/Amsterdam`. `DATABASE_URL` is already injected by Neon.
   - Redeploy, then note the project URL (`https://….vercel.app`). The default name `happy-gate-food-ordering.vercel.app`
     returned DEPLOYMENT_NOT_FOUND, so the real URL is different or the first deploy has not run yet.
   - Check that the GitHub repo is connected so pushes to `master` deploy automatically.
2. [ ] **Seed the menu once** into the fresh database (Claude can run this once given URL + token):
   `curl -X POST -H "Authorization: Bearer $KITCHEN_TOKEN" https://<url>/api/restaurant/admin/seed-menu`
3. [ ] **Smoke test the backend**: `/` shows the menu, `/kitchen?token=…` opens, `/api/restaurant/ping` returns `ok`
4. [ ] **Delete or suspend the old Render web service** in the Render dashboard (code no longer supports it)
5. [ ] **Compile the app** in Android Studio (never built anywhere yet; report compile errors to fix)
6. [ ] **Connect the app**: Vercel URL + `KITCHEN_TOKEN`; allow notifications
7. [ ] **Firebase** (background alerts): create project, add Android app `nl.happygate.orders`, put
   `google-services.json` in `app/`, rebuild; set `FIREBASE_CREDENTIALS` (service-account JSON) on Vercel and redeploy
8. [ ] **Printer**: confirm exact Epson model and that it is LAN/Wi‑Fi; give it a fixed IP on the router;
   Settings → *Test print* from the phone on the restaurant Wi‑Fi
9. [ ] **Phone setup**: "New orders" channel sound on, battery optimisation off for the app, consider a Do Not Disturb exception
10. [ ] **End-to-end test**: order on the website → phone rings → accept 30 min → customer email shows the time →
    ticket prints → mark ready → customer email "ready"

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
