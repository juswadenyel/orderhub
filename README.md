# OrderHub

A modular monolith: one Spring Boot app with two in-process modules
(`shop` = Order, `inventory` = Inventory) sharing one Supabase (Postgres)
database, plus a React (Vite) frontend that talks to it over REST.

```
orderhub/
├── backend/    Spring Boot app (edu.cit.dingding.shop, edu.cit.dingding.inventory)
├── frontend/   React + Vite UI
└── sql/        supabase_schema.sql — table creation + seed data
```

---

## 1. Supabase setup (step by step)

1. Go to your Supabase project → left sidebar → **SQL Editor** → **New query**.
2. Open `sql/supabase_schema.sql` from this repo, copy all of it, paste it into
   the editor, and click **Run**. This creates the `inventory` and `orders`
   tables and seeds three products (Wireless Mouse: 25, Mechanical Keyboard:
   10, USB-C Hub: 0 — the Hub is seeded at 0 on purpose, so you have a product
   that will always trigger a REJECTED order).
3. Go to **Project Settings → Database → Connection string**. Use the
   **URI** / session pooler connection string (port `5432` or `6543`
   depending on what Supabase shows you), and note your database password.
4. Build a JDBC URL out of that, in this shape:
   ```
   jdbc:postgresql://<host>:<port>/postgres?sslmode=require
   ```
   Your username is usually `postgres` (or `postgres.<project-ref>` if
   you're using the pooler connection — Supabase shows you the exact
   username on that same settings page).

## 2. Environment variables (never commit these)

The backend reads its database credentials from environment variables —
nothing is hardcoded, and `.env` / local property files are already in
`.gitignore`.

Set these before running the backend:

```bash
export SUPABASE_DB_URL="jdbc:postgresql://<host>:<port>/postgres?sslmode=require"
export SUPABASE_DB_USER="postgres"
export SUPABASE_DB_PASSWORD="<your-db-password>"
```

(On Windows PowerShell, use `$env:SUPABASE_DB_URL = "..."` instead.)

## 3. Run the backend

```bash
cd backend
mvn spring-boot:run
```

(No Maven wrapper is bundled here — if you don't have Maven installed,
just open the `backend` folder in IntelliJ/VS Code and run
`OrderHubApplication`'s `main` method instead; the IDE handles the build.)

It starts on `http://localhost:8080`. `POST /api/orders` and
`GET /api/inventory` are the two endpoints the frontend uses.

## 4. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

It starts on `http://localhost:5173` (Vite's default — this is also the
origin the backend's CORS config allows by default). Open that URL, pick a
product, submit an order, and watch the stock number and stamp update.

To test both paths:
- **CONFIRMED**: order the Wireless Mouse or Mechanical Keyboard with a
  quantity at or under their current stock.
- **REJECTED**: order the USB-C Hub (seeded at 0 stock), or order any
  product with a quantity higher than its current stock.

## 5. Network tab evidence

_[Add your two screenshots here: one showing the `POST /api/orders` request/
response for a CONFIRMED order, one for a REJECTED order — open your
browser's DevTools → Network tab → click the `orders` request → Response
tab, and screenshot both the request payload and response body.]_

---

## Reflection

**1. In-process vs. microservices — what do you get for free, and what
would you need to add back if split?**

Right now, `OrderService` calling `InventoryService.reserve(...)` is a
plain Java method call inside one JVM. That gives me a few things for
free: the call is synchronous and effectively instant, it can't fail from
a network timeout or a DNS problem, and  most importantly  it's
transactional. Because both the inventory update and the order write
happen inside one Spring `@Transactional` boundary against one database,
there's no window where stock gets decremented but the order never gets
recorded, or vice versa. If I split Inventory into its own microservice,
all of that has to be rebuilt by hand: I'd need a network client (REST or
gRPC) with retries and timeouts, a way to handle Inventory being slow or
down (circuit breaker, fallback), and since I'd lose the single local
transaction — either a distributed transaction protocol or, more
realistically, a saga: reserve stock first, then write the order, and if
the order write fails, fire a compensating "release stock" call back to
Inventory. I'd also need to think about idempotency, since a retried
network call could double-reserve stock if I'm not careful.

**2. Why does package-private `InventoryServiceImpl` matter?**

Marking `InventoryServiceImpl` (and `InventoryRepository`) package-private
means the Java compiler itself enforces the module boundary  nobody
outside `edu.cit.dingding.inventory` can import, construct, or cast to
that class, so `OrderService` is physically incapable of skipping the
interface and poking at the repository or the entity directly. If
`InventoryServiceImpl` were `public`, that guarantee disappears: it's just
a convention someone could ignore under deadline pressure, e.g. autowiring
`InventoryRepository` straight into `OrderService` to "save a call." That
one shortcut would quietly couple the two modules to each other's
internals, make it much harder to split Inventory out later (since now
Order code depends on Inventory's JPA entities and table structure, not
just its public contract), and remove the compiler's ability to catch the
mistake — it would only show up in a code review, if at all.

**3. When would I extract Inventory into its own microservice, and what
would change?**

I'd consider extracting it once Inventory has genuinely different scaling
or release needs than Order for example, if inventory reads/writes
became a much bigger load (barcode scanners hitting it constantly) than
order placement, or if a different team started owning it and needed to
deploy it independently. Code-wise, `InventoryServiceImpl` would move into
its own Spring Boot app behind a REST (or messaging) API. In the Order
module, I'd write a new implementation of the same `InventoryService`
interface — an `InventoryServiceClient` that makes an HTTP call instead of
a local method call and swap it in via Spring configuration. Because
`OrderService` only ever depended on the `InventoryService` interface, it
wouldn't need to change at all; only the implementation wired in behind it
would.
