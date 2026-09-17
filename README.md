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

<img width="1919" height="1079" alt="image" src="https://github.com/user-attachments/assets/b118fb65-e736-425f-b850-439690be80c1" />
<img width="1919" height="1079" alt="image" src="https://github.com/user-attachments/assets/9ac9a41c-a228-4ea0-80e7-6d2a29a9a8dc" />
<img width="1919" height="1079" alt="image" src="https://github.com/user-attachments/assets/ce001761-c685-4ba5-977c-4c25be5c8345" />
<img width="1910" height="1077" alt="image" src="https://github.com/user-attachments/assets/0d7a6ce6-5b54-428f-964f-dc5ac53a83c2" />
<img width="1919" height="1079" alt="image" src="https://github.com/user-attachments/assets/70767a64-0aae-46e6-9715-2aa7e30fc00c" />






---

## Reflection

**1. What keeps multi-item orders atomic in-process, and what would you
need to add if Order and Inventory were split across a network?**

Right now, atomicity comes from two things stacking together: validating
every line item against current stock *before* reserving anything, and
wrapping the whole `placeOrder` method in one `@Transactional` boundary
against a single database. Because the validation loop runs first and
`reserve()` is only ever called once every line has already passed, there's
no scenario where three items get reserved and a fourth fails halfway
through — the "failure" already happened during validation, before any
write occurred. If Order and Inventory were split into separate services,
I'd lose both guarantees. The validate-then-commit pattern would still work
in spirit, but a "validated" stock level could go stale before the real
reservation call arrives, if another order grabs that stock in between.
I'd need either a two-phase hold-then-commit approach with a timeout that
releases stale holds, or a saga:
reserve items one at a time and, if a later item in the same order fails,
fire compensating "release" calls back to Inventory for every item already
reserved. Either way, "all-or-nothing" stops being free — it becomes code I
have to write and test myself.

**2. How does publishing an event change the coupling between OrderService
and Notification, and what would a real microservice split need?**

`OrderService` doesn't know Notification exists — it publishes a plain
`OrderPlacedEvent`/`OrderRejectedEvent` object and moves on; whether zero
listeners or five are subscribed doesn't change a line of its code. That's
a big drop in coupling compared to Lab 1's Order→Inventory relationship,
where Order explicitly holds and calls an `InventoryService` reference. The
catch is that in-process `ApplicationEventPublisher` delivery is
synchronous and only reliable within one JVM — if Notification became its
own microservice, "publish an event" would need to become "publish to a
message broker" (Kafka, RabbitMQ, SQS), and I'd need to actually think
about delivery guarantees: at-least-once delivery with an idempotent
consumer (so a retried message doesn't double-log), a dead-letter queue for
messages Notification can't process, and ordering, which a broker doesn't
guarantee for free the way one JVM thread does.

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
