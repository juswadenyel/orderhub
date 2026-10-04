# INTEGRATION.md — LegacySupply (Lab 3)

Everything marked `[FILL IN]` needs your real API key to answer — probe
LegacySupply with Postman/curl for these (Stage 0 of Lab 4 also allows
Postman against Tiangge, but ONLY before your app goes live).

## Product mapping

Run `GET /catalog` with your session token for real SupplierSku/PackSize values:

| Our product_id | Our name | LegacySupply SupplierSku | PackSize |
|---|---|---|---|
| P100 | Wireless Mouse | `[FILL IN]` | `[FILL IN]` |
| P200 | Mechanical Keyboard | `[FILL IN]` | `[FILL IN]` |
| P300 | USB-C Hub | `[FILL IN]` | `[FILL IN]` |

Put real values in `backend/src/main/resources/application-local.properties`
as `legacysupply.mapping.P100=SKU:packSize` — and use the SAME SupplierSku
in the matching `tiangge.listing.P100=sellerSku:title:supplierSku` line,
since a Tiangge listing and its LegacySupply reorder must point at the
same catalog item.

## Sessions

`POST /auth/token` with `ClientId`+`ApiKey` returns a `SessionToken`, sent
back as `X-LS-Session` on every other call. The manual doesn't state a
lifetime — `[FILL IN]`: measure it by polling an authenticated endpoint
(e.g. `GET /catalog`) with growing delays until you get `E-AUTH-03`/
`E-AUTH-07` instead of 200. `LegacySupplyClient` doesn't need this number
to work correctly — it refreshes reactively (catches the 401, re-
authenticates, retries once) rather than guessing a TTL.

## Error codes actually received

| Code | HTTP | Meaning | What caused it for me |
|---|---|---|---|
| E-AUTH-01 | 401 | Credentials rejected | `[FILL IN]` |
| E-AUTH-02/03/07 | 401 | Session missing/not recognized/not valid | `[FILL IN]` |
| E-FMT-01/02 | 415/400 | Unsupported media / malformed document | `[FILL IN]` |
| E-REF-05 | 400 | BuyerRef invalid | `[FILL IN]` |
| E-SKU-02 | 422 | Item not recognized | `[FILL IN]` |
| E-QTY-11 | 422 | Quantity invalid | `[FILL IN]` |
| E-IDEM-04 | 409 | Request id reused with different content | `[FILL IN]` |
| E-PO-04 | 404 | Order not found | `[FILL IN]` |
| E-RATE-03 | 429 | Request quota exceeded | `[FILL IN]` |
| E-SYS-50/99 | 503 | Processing error / unavailable | `[FILL IN]` |

In code (`LegacySupplyClient.handleResponse`): E-SKU-02, E-QTY-11,
E-REF-05, E-FMT-01, E-FMT-02, E-AUTH-01, E-QRY-06, E-PO-04 are **permanent**
(retrying won't help); everything else is **transient** and goes through
the retry/backoff loop.

## Qty and Uom, in plain terms

`Qty` counts **cases**, not units — each `SupplierSku` has a `PackSize`
(units per case). Example: `SupplierSku ABC-1234` with `PackSize 12`; if
Inventory needs 20 more units, `SupplierGateway.reorder()` sends
`Qty = ceil(20/12) = 2` cases (24 units) — rounding **up**, since rounding
down would silently under-order and ordering a fractional case isn't possible.

## Unexpected status codes

Only 10/20/30/40 are documented. `SupplierGatewayImpl.mapStatusCode()`
logs anything else to stderr and leaves the order's local status as
`ACCEPTED` ("still in flight") rather than guessing — marking an unknown
code `DELIVERED` risks a restock for units that never arrived. `[FILL IN
if you actually saw one]`.

## Request quota

`[FILL IN]`: note if you hit `E-RATE-03`, and whether `SupplierOrderScheduler`'s
20s/15s intervals (retry/tracking) needed loosening for your quota.
