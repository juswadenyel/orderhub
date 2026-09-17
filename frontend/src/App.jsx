import { useEffect, useMemo, useState } from 'react'
import { fetchInventory, fetchOrders, fetchNotifications, placeOrder, cancelOrder } from './api.js'

const MAX_BAR_STOCK = 30
const LOW_STOCK_THRESHOLD = 5

function stockLevel(stock) {
  if (stock === 0) return 'out'
  if (stock < LOW_STOCK_THRESHOLD) return 'low'
  return 'ok'
}

function StockBar({ stock }) {
  const level = stockLevel(stock)
  const width = Math.min(100, Math.round((stock / MAX_BAR_STOCK) * 100))
  return (
    <div className="stock-bar" aria-hidden="true">
      <div className={`stock-bar-fill stock-bar-fill--${level}`} style={{ width: `${width}%` }} />
    </div>
  )
}

function InventoryPanel({ items, loading, error }) {
  return (
    <section className="panel">
      <h2 className="panel-title">Live inventory</h2>
      {loading && <p className="muted">Loading stock levels…</p>}
      {error && <p className="error-text">{error}</p>}
      {!loading && !error && (
        <ul className="inventory-list">
          {items.map((item) => {
            const level = stockLevel(item.stock)
            return (
              <li key={item.productId} className={`inventory-row inventory-row--${level}`}>
                <div className="inventory-row-top">
                  <span className="inventory-name">{item.name}</span>
                  <span className="inventory-id">{item.productId}</span>
                </div>
                <div className="inventory-row-bottom">
                  <StockBar stock={item.stock} />
                  <span className={`inventory-stock inventory-stock--${level}`}>
                    {item.stock} in stock{level === 'low' && ' · low'}
                  </span>
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}

function ResultStamp({ result }) {
  if (!result) return null
  const isConfirmed = result.status === 'CONFIRMED'
  return (
    <div className={`stamp stamp--${isConfirmed ? 'confirmed' : 'rejected'}`} role="status">
      <span className="stamp-text">{isConfirmed ? 'Confirmed' : 'Rejected'}</span>
      {result.reason && <p className="stamp-reason">{result.reason}</p>}
    </div>
  )
}

function CartPanel({ items, cart, addProductId, setAddProductId, addQuantity, setAddQuantity,
  onAddToCart, onUpdateQuantity, onRemove, onSubmit, submitting, formError, result, nameFor }) {
  return (
    <section className="panel">
      <h2 className="panel-title">Place an order</h2>

      <div className="add-row">
        <select
          className="field-input"
          value={addProductId}
          onChange={(e) => setAddProductId(e.target.value)}
          disabled={items.length === 0}
        >
          {items.map((item) => (
            <option key={item.productId} value={item.productId}>
              {item.name} ({item.productId})
            </option>
          ))}
        </select>
        <input
          className="field-input add-row-qty"
          type="number"
          min="1"
          value={addQuantity}
          onChange={(e) => setAddQuantity(e.target.value)}
        />
        <button type="button" className="add-btn" onClick={onAddToCart}>
          Add
        </button>
      </div>

      {cart.length === 0 ? (
        <p className="muted cart-empty">Your cart is empty — add a product above.</p>
      ) : (
        <ul className="cart-list">
          {cart.map((line) => (
            <li key={line.productId} className="cart-row">
              <span className="cart-name">{nameFor(line.productId)}</span>
              <input
                className="field-input cart-qty"
                type="number"
                min="1"
                value={line.quantity}
                onChange={(e) => onUpdateQuantity(line.productId, e.target.value)}
              />
              <button type="button" className="remove-btn" onClick={() => onRemove(line.productId)} aria-label="Remove">
                ✕
              </button>
            </li>
          ))}
        </ul>
      )}

      {formError && <p className="error-text">{formError}</p>}

      <button className="submit-btn" type="button" onClick={onSubmit} disabled={submitting || cart.length === 0}>
        {submitting ? 'Submitting…' : 'Submit order'}
      </button>

      <ResultStamp result={result} />
    </section>
  )
}

function statusClass(status) {
  if (status === 'CONFIRMED') return 'badge--confirmed'
  if (status === 'REJECTED') return 'badge--rejected'
  return 'badge--cancelled'
}

function OrderHistoryPanel({ orders, loading, error, onCancel, cancellingId, nameFor }) {
  return (
    <section className="panel">
      <h2 className="panel-title">Order history</h2>
      {loading && <p className="muted">Loading orders…</p>}
      {error && <p className="error-text">{error}</p>}
      {!loading && !error && orders.length === 0 && <p className="muted">No orders yet.</p>}
      {!loading && !error && orders.length > 0 && (
        <ul className="order-list">
          {orders
            .slice()
            .sort((a, b) => b.orderId - a.orderId)
            .map((order) => (
              <li key={order.orderId} className="order-row">
                <div className="order-row-top">
                  <span className="order-id">#{order.orderId}</span>
                  <span className={`badge ${statusClass(order.status)}`}>{order.status}</span>
                </div>
                <ul className="order-items">
                  {order.items.map((line, idx) => (
                    <li key={idx}>
                      {nameFor(line.productId)} × {line.quantity}
                    </li>
                  ))}
                </ul>
                {order.reason && <p className="order-reason">{order.reason}</p>}
                {order.status === 'CONFIRMED' && (
                  <button
                    type="button"
                    className="cancel-btn"
                    onClick={() => onCancel(order.orderId)}
                    disabled={cancellingId === order.orderId}
                  >
                    {cancellingId === order.orderId ? 'Cancelling…' : 'Cancel'}
                  </button>
                )}
              </li>
            ))}
        </ul>
      )}
    </section>
  )
}

function NotificationPanel({ notifications, loading, error }) {
  return (
    <section className="panel">
      <h2 className="panel-title">Activity feed</h2>
      {loading && <p className="muted">Loading activity…</p>}
      {error && <p className="error-text">{error}</p>}
      {!loading && !error && notifications.length === 0 && <p className="muted">Nothing yet.</p>}
      {!loading && !error && notifications.length > 0 && (
        <ul className="feed-list">
          {notifications
            .slice()
            .sort((a, b) => b.notificationId - a.notificationId)
            .map((n) => (
              <li key={n.notificationId} className="feed-row">
                <span className="feed-dot" aria-hidden="true" />
                <span className="feed-message">{n.message}</span>
              </li>
            ))}
        </ul>
      )}
    </section>
  )
}

export default function App() {
  const [items, setItems] = useState([])
  const [loadingInventory, setLoadingInventory] = useState(true)
  const [inventoryError, setInventoryError] = useState('')

  const [orders, setOrders] = useState([])
  const [loadingOrders, setLoadingOrders] = useState(true)
  const [ordersError, setOrdersError] = useState('')

  const [notifications, setNotifications] = useState([])
  const [loadingNotifications, setLoadingNotifications] = useState(true)
  const [notificationsError, setNotificationsError] = useState('')

  const [cart, setCart] = useState([])
  const [addProductId, setAddProductId] = useState('')
  const [addQuantity, setAddQuantity] = useState(1)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')
  const [result, setResult] = useState(null)
  const [cancellingId, setCancellingId] = useState(null)

  const nameFor = useMemo(() => {
    const map = new Map(items.map((i) => [i.productId, i.name]))
    return (productId) => map.get(productId) || productId
  }, [items])

  async function loadInventory() {
    try {
      setLoadingInventory(true)
      setInventoryError('')
      const data = await fetchInventory()
      setItems(data)
      if (data.length > 0 && !addProductId) {
        setAddProductId(data[0].productId)
      }
    } catch (err) {
      setInventoryError(err.message)
    } finally {
      setLoadingInventory(false)
    }
  }

  async function loadOrders() {
    try {
      setLoadingOrders(true)
      setOrdersError('')
      setOrders(await fetchOrders())
    } catch (err) {
      setOrdersError(err.message)
    } finally {
      setLoadingOrders(false)
    }
  }

  async function loadNotifications() {
    try {
      setLoadingNotifications(true)
      setNotificationsError('')
      setNotifications(await fetchNotifications())
    } catch (err) {
      setNotificationsError(err.message)
    } finally {
      setLoadingNotifications(false)
    }
  }

  function loadAll() {
    loadInventory()
    loadOrders()
    loadNotifications()
  }

  useEffect(() => {
    loadAll()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function handleAddToCart() {
    if (!addProductId) return
    const qty = Number(addQuantity)
    if (!qty || qty < 1) return

    setCart((prev) => {
      const existing = prev.find((line) => line.productId === addProductId)
      if (existing) {
        return prev.map((line) =>
          line.productId === addProductId ? { ...line, quantity: line.quantity + qty } : line
        )
      }
      return [...prev, { productId: addProductId, quantity: qty }]
    })
    setAddQuantity(1)
  }

  function handleUpdateQuantity(productId, value) {
    const qty = Number(value)
    setCart((prev) =>
      prev.map((line) => (line.productId === productId ? { ...line, quantity: qty } : line))
    )
  }

  function handleRemoveFromCart(productId) {
    setCart((prev) => prev.filter((line) => line.productId !== productId))
  }

  async function handleSubmitOrder() {
    setFormError('')
    setResult(null)

    if (cart.length === 0) {
      setFormError('Add at least one product to the cart first.')
      return
    }
    if (cart.some((line) => !line.quantity || line.quantity < 1)) {
      setFormError('Every line needs a quantity of at least 1.')
      return
    }

    setSubmitting(true)
    try {
      const response = await placeOrder(cart)
      setResult(response)
      setCart([])
      loadAll()
    } catch (err) {
      setFormError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  async function handleCancel(orderId) {
    setCancellingId(orderId)
    try {
      await cancelOrder(orderId)
      loadAll()
    } catch (err) {
      setOrdersError(err.message)
    } finally {
      setCancellingId(null)
    }
  }

  return (
    <div className="page">
      <header className="page-header">
        <div className="wordmark">
          Order<span className="wordmark-accent">Hub</span>
        </div>
        <p className="page-subtitle">Inventory & order console</p>
      </header>

      <main className="layout">
        <CartPanel
          items={items}
          cart={cart}
          addProductId={addProductId}
          setAddProductId={setAddProductId}
          addQuantity={addQuantity}
          setAddQuantity={setAddQuantity}
          onAddToCart={handleAddToCart}
          onUpdateQuantity={handleUpdateQuantity}
          onRemove={handleRemoveFromCart}
          onSubmit={handleSubmitOrder}
          submitting={submitting}
          formError={formError}
          result={result}
          nameFor={nameFor}
        />
        <InventoryPanel items={items} loading={loadingInventory} error={inventoryError} />
        <OrderHistoryPanel
          orders={orders}
          loading={loadingOrders}
          error={ordersError}
          onCancel={handleCancel}
          cancellingId={cancellingId}
          nameFor={nameFor}
        />
        <NotificationPanel
          notifications={notifications}
          loading={loadingNotifications}
          error={notificationsError}
        />
      </main>
    </div>
  )
}
