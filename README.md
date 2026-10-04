# OrderHub

A modular monolith: one Spring Boot app with three in-process modules —
`shop` (Order), `inventory` (Inventory), and `notification` (Notification,
new in Lab 2) — sharing one Supabase (Postgres) database, plus a React
(Vite) frontend that talks to it over REST.

```
orderhub/
├── backend/    Spring Boot app (edu.cit.dingding.shop, .inventory, .notification)
├── frontend/   React + Vite UI
├── sql/        supabase_schema.sql — table creation + seed data (recreates everything, Lab 1 + Lab 2)
└── README.md
```

---

## 1. Supabase setup (step by step)

1. Go to your Supabase project → left sidebar → **SQL Editor** → **New query**.
2. Open `sql/supabase_schema.sql`, copy all of it, paste it into the editor,
   and click **Run**. This drops and recreates every table from scratch —
   `inventory`, `orders`, the new `order_items`, and the new `notifications`
   — and reseeds the three products (Wireless Mouse: 25, Mechanical
   Keyboard: 10, USB-C Hub: 0). Re-running it any time gives you a clean
   slate.
3. Get your connection details from **Project Settings → Database →
   Connection string** as in Lab 1.

## 2. Environment variables (unchanged from Lab 1, never commit these)

```bash
export SUPABASE_DB_URL="jdbc:postgresql://<host>:<port>/postgres?sslmode=require"
export SUPABASE_DB_USER="postgres"
export SUPABASE_DB_PASSWORD="<your-db-password>"
```

## 3. Run the backend

```bash
cd backend
mvn spring-boot:run
```

(Or use your IDE's Run button on `OrderHubApplication`, with the same three
variables set in its launch configuration.) Starts on `http://localhost:8080`.

## 4. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

Starts on `http://localhost:5173`.

## 5. What's new in Lab 2

**Multi-item orders.** `POST /api/orders` now takes `{ items: [{ productId,
quantity }, ...] }` instead of a single product. Every line is validated
against current stock **before** anything is reserved — if any one line
would fail, the whole order is REJECTED and nothing is reserved (see
`OrderService.placeOrder`).

**Order cancellation.** `POST /api/orders/{orderId}/cancel` cancels a
CONFIRMED order and returns every reserved line item's quantity to stock via
the new `InventoryService.restock(...)`. Returns 404 if the order doesn't
exist, 409 if it's already cancelled.

**Read endpoints.** `GET /api/inventory` (unchanged from Lab 1), plus new
`GET /api/orders` (full order history with line items) and
`GET /api/notifications` (the activity feed).

**Domain events (Notification module).** `OrderService` never calls the
notification module directly — it publishes `OrderPlacedEvent` /
`OrderRejectedEvent` (in `edu.cit.dingding.shop.events`) through Spring's
`ApplicationEventPublisher`. `InventoryServiceImpl` similarly publishes a
`LowStockEvent` (in `edu.cit.dingding.inventory.events`) whenever a
`reserve()` leaves a product below 5 units. `NotificationEventListener`
listens for all three with `@EventListener` and writes a row to
`notifications`. It only ever imports those three plain event classes —
never `OrderService`, `InventoryService`, or their impls.

**Events are synchronous (no `@Async`).** For a lab this size, keeping
listeners synchronous means a notification is guaranteed to exist by the
time the HTTP response comes back, which makes the "capture Network tab +
activity feed" test cases straightforward and deterministic. The tradeoff
is that a slow listener would add latency to the request — not a concern
here since writing one row is fast, but it's the reason a real production
system would likely make this `@Async` with its own thread pool once
notification logic got heavier (e.g. calling an email provider).

## 6. Testing both paths + Network tab evidence

Test and screenshot all four of these (DevTools → Network tab → click the
request → Response tab):

1. **Multi-item order, all succeed** — add two in-stock products to the
   cart (e.g. 2 Mice + 1 Keyboard) and submit. Expect `CONFIRMED`.
2. **Multi-item order, one fails → whole order REJECTED** — add one
   in-stock product plus the USB-C Hub (0 stock) and submit. Expect
   `REJECTED` with **no** stock deducted from the in-stock item — check
   `GET /api/inventory` before and after to confirm nothing was reserved.
3. **Cancel + restock** — cancel a CONFIRMED order from the order history
   panel, then check `GET /api/inventory` shows the stock back where it was.
4. **Activity feed** — after doing the above, `GET /api/notifications` (or
   the Activity feed panel) should show a confirmed-order entry, a
   rejected-order entry, and — if any product dropped under 5 units along
   the way — a "Reorder needed" entry.

<img width="1919" height="1033" alt="image" src="https://github.com/user-attachments/assets/b24019e5-e833-4606-89b0-3bc7ed0f357c" />
<img width="1918" height="1027" alt="image" src="https://github.com/user-attachments/assets/31db9e78-0b21-4f8d-a29f-76c8eab39c42" />
<img width="1919" height="1031" alt="image" src="https://github.com/user-attachments/assets/9fa4de5e-e7df-4328-ae9a-db1ec44c4470" />
<img width="1919" height="1030" alt="image" src="https://github.com/user-attachments/assets/ec59a10e-12d4-405a-b929-6225ae50a33b" />
<img width="1919" height="1031" alt="image" src="https://github.com/user-attachments/assets/66621fea-8513-401d-bf7e-feffc939df54" />











---

## Reflection

 1. Duplicate BuyerRef RO-3

LegacySupply has two orders for RO-3: PO-103135 at 19:41:29 and PO-100179 at 20:31:41. The first POST received a 503 even though the supplier created PO-103135. The record proves the duplicate BuyerRef and the uncertain response, but does not include enough of the request history to identify which later code path created PO-100179. The risk in the original adapter was real: a failed BuyerRef lookup fell through to another POST, and repeated low stock events could also make separate reorder requests. `SupplierGatewayImpl.reorder` now returns an existing open order for that product, and `SupplierOrderScheduler` defers a retry whenever BuyerRef lookup fails; it does not POST until it has established the earlier request was not created.

2. The 503 for PO-103135

The placement request used BuyerRef RO-3 and X-Request-Id `c37bf927-9148-47a8-b949-d561cbf720c1`. LegacySupply returned 503 after creating PO-103135, so the adapter treated the result as transient and retained the local purchase order as pending. On later retry, the scheduler searches LegacySupply by the same BuyerRef and adopts the existing PO number and status; if that search itself fails, the updated code waits for the next polling tick instead of placing another order. The stable request ID also makes retries of the same POST recognizable to LegacySupply's idempotency handling.


**3. Which one module would you extract first, and what changes?**

Notification, without much hesitation. It has no write dependency from
Order or Inventory (nothing calls into it — it only listens), so extracting
it can't break either of the other two modules' core order-placement logic
even if Notification is down or slow. Concretely: move `Notification`,
`NotificationRepository`, `NotificationServiceImpl`, and
`NotificationEventListener` into their own Spring Boot app with their own
database table; replace the in-process `ApplicationEventPublisher` calls in
`OrderService`/`InventoryServiceImpl` with publishing the same event
payloads to a message broker topic instead; and have the new Notification
service subscribe to that topic. Because the event classes were already
isolated in their own `events` sub-packages and Order/Inventory never
depended on Notification's internals, this split touches almost nothing in the
other two modules — just where the event goes after `publishEvent()` runs.

---

## 3. Backorder TG-MGKGVW and PO-103134

FeedPoller mapped the Tiangge order lines and OrderService recorded TG-MGKGVW as BACKORDERED because P-1002 did not have enough stock but had an open supplier purchase order. SupplierOrderScheduler polled PO-103134 until LegacySupply reported status 40, then emitted `SupplierOrderDeliveredEvent` with the product, delivered units, and PO number. InventoryReplenishmentListener added those units to P-1002, and BackorderResolutionListener rechecked all lines of each affected backorder. Once all lines were available it reserved them and emitted the resolution event that let FeedPoller notify Tiangge that the order was accepted.

## Lab 4: Tiangge marketplace channel

`edu.cit.dingding.channel` — a new module that polls Tiangge's order feed
and drives the SAME `OrderService`/`InventoryService` the React UI uses.
Order and Inventory have zero imports from this package.

**Flow, end to end:**
1. `StartupChannelInitializer` sends the first heartbeat, publishes
   listings, then publishes current stock — in that exact order, at boot.
2. `HeartbeatScheduler` keeps sending one every 30s after that.
3. `FeedPoller` polls `GET /feed` every 5s. For each new `eventId`
   (deduped against `channel_events`), it either creates a real order via
   `OrderService.placeOrder(items, allowBackorder=true)` or cancels one via
   `OrderService.cancelOrder(...)` — then reports the outcome back to
   Tiangge. The Tiangge order ID is only ever stored in
   `channel_order_mappings`; Order/Inventory never see it.
4. `StockSyncListener` listens for `InventoryStockChangedEvent` — a new
   event `InventoryServiceImpl` publishes after **every** stock mutation,
   from any source — and immediately pushes the new number to Tiangge.
   Event-driven, never on a timer, so this also covers React-UI orders and
   cancellations, not just Tiangge ones.
5. Backorders: `OrderService.placeOrder(items, true)` checks
   `SupplierGateway.hasOpenPurchaseOrder(productId)` for every short item
   before deciding REJECTED vs BACKORDERED. When a delivery lands,
   `BackorderResolutionListener` (in shop) re-checks every BACKORDERED
   order and confirms or cancels it, publishing
   `OrderBackorderResolvedEvent`; `BackorderResolutionNotifier` (in
   channel) is what actually tells Tiangge.

### Setup

1. Fill in `application-local.properties`: `tiangge.client-id`/`api-key`
   (same key as LegacySupply, per the manual) and the three
   `tiangge.listing.P1__=sellerSku:title:supplierSku` lines — keep the
   `supplierSku` consistent with your `legacysupply.mapping.*` entries.
2. **Double-check `tiangge.base-url`** in `application.properties` — the
   manual's "Getting started" section shows the path pattern
   (`https://…/tiangge/v1`) but redacts the exact host. I've set it to
   `https://legacysupply.onrender.com/tiangge/v1` by inference from the
   docs URL pattern — confirm this actually resolves with a single Postman
   `GET` **before your app goes live** (Stage 0 explicitly allows this).
3. Re-run `sql/supabase_schema.sql` (adds `channel_events`,
   `channel_order_mappings`, `channel_cursor`, and the previously-missing
   `supplier_orders`).
4. `mvn spring-boot:run`. Watch the console for `[channel] First heartbeat
   sent` and `Shop should now be live on Tiangge` — that's Task 1 + 2 done.

### On running this for the actual grading stages

- **The app must keep running continuously** between going live and your
  hands-off test — Stage 1's proof is "online for at least 80% of the
  time." Don't stop/restart it except deliberately for the Stage 4 restart
  test.
- **Only this app may call Tiangge/LegacySupply** — don't use Postman
  against either once you're live; the rules say those calls count against
  you, and the self-check page is reading server-side traffic, not
  anything you can fake from outside your app.
- **Stage 4 (restart test)**: stop the app, wait over a minute, start it
  again, and don't touch anything else — `channel_cursor` and
  `channel_events` are what make it resume instead of reprocessing.
- **Stage 5 (hands-off, 10 minutes, worth the most points)**: only press
  "Start hands-off test" once Stages 1–4 are all green on the self-check
  page — you get at most two attempts.
