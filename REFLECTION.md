# Integration reflection

## LegacySupply

### 1. Duplicate BuyerRef RO-3

LegacySupply has two orders for RO-3: PO-103135 at 19:41:29 and PO-100179 at 20:31:41. The first POST received a 503 even though the supplier created PO-103135. The record proves the duplicate BuyerRef and the uncertain response, but does not include enough of the request history to identify which later code path created PO-100179. The risk in the original adapter was real: a failed BuyerRef lookup fell through to another POST, and repeated low stock events could also make separate reorder requests. `SupplierGatewayImpl.reorder` now returns an existing open order for that product, and `SupplierOrderScheduler` defers a retry whenever BuyerRef lookup fails; it does not POST until it has established the earlier request was not created.

### 2. The 503 for PO-103135

The placement request used BuyerRef RO-3 and X-Request-Id `c37bf927-9148-47a8-b949-d561cbf720c1`. LegacySupply returned 503 after creating PO-103135, so the adapter treated the result as transient and retained the local purchase order as pending. On later retry, the scheduler searches LegacySupply by the same BuyerRef and adopts the existing PO number and status; if that search itself fails, the updated code waits for the next polling tick instead of placing another order. The stable request ID also makes retries of the same POST recognizable to LegacySupply's idempotency handling.

### 3. StatusCode 90 on PO-103133

The documented statuses stopped at 40, so I used the observed record: repeated status polls returned 90, and the PO never appeared as delivered. Treating an unknown code as ACCEPTED would leave phantom incoming stock and backorders waiting forever. The adapter now maps 90 to terminal `CANCELLED`, never emits a delivery/restock event for it, and notifies backorder resolution so orders with no remaining open supply can be cancelled. The Supabase status constraint must include `CANCELLED`; `sql/migrate_supplier_cancelled_status.sql` applies that change to an existing database.

## Tiangge marketplace

### 1. P-1002 was accepted with published stock 0

At 19:45:09 Tiangge accepted four P-1002 units while both the last published stock and its own decision ledger were zero. The application used its local inventory value when deciding, but stock publication was an after-commit network call based on event values. Concurrent changes could publish out of order, leaving Tiangge with an older figure than the one OrderService read. Stock sync now serializes publications and rereads the committed inventory value before sending; a 30-second reconciliation also republishes the current values after a temporary failure. OrderService now acquires inventory row locks in sorted product order before validating an order, preventing concurrent orders from approving the same remaining units.

### 2. Duplicate delivery of evt_46a0e607f22f7919

`channel_events.event_id` is the durable event ledger, and `channel_order_mappings.tiangge_order_id` is unique. On processing, FeedPoller first checks the event ID, then uses the marketplace order mapping to find the local order and resend its saved decision instead of placing a second local order. The stored mapping carries the local order ID and `last_decision`; the event row and `channel_cursor` survive process restarts in Postgres. If the process restarts between the two deliveries, it reads the saved cursor and event ledger, sees the event was processed, and skips it. The order mapping is an additional safeguard if the same order is delivered under a different feed event ID.

### 3. Backorder TG-MGKGVW and PO-103134

FeedPoller mapped the Tiangge order lines and OrderService recorded TG-MGKGVW as BACKORDERED because P-1002 did not have enough stock but had an open supplier purchase order. SupplierOrderScheduler polled PO-103134 until LegacySupply reported status 40, then emitted `SupplierOrderDeliveredEvent` with the product, delivered units, and PO number. InventoryReplenishmentListener added those units to P-1002, and BackorderResolutionListener rechecked all lines of each affected backorder. Once all lines were available it reserved them and emitted the resolution event that let FeedPoller notify Tiangge that the order was accepted.
