import { useEffect, useMemo, useState } from 'react'
import {
  fetchInventory,
  fetchOrders,
  fetchNotifications,
  placeOrder,
  cancelOrder,
} from './api.js'

const MAX_BAR_STOCK = 30
const LOW_STOCK_THRESHOLD = 5

function stockLevel(stock) {
  if (stock === 0) return 'out'
  if (stock < LOW_STOCK_THRESHOLD) return 'low'
  return 'ok'
}

function todayStub() {
  const d = new Date()
  return d.toLocaleDateString('en-US', { month: 'short', day: '2-digit', year: 'numeric' }).toUpperCase()
}

function StockBar({ stock }) {
  const level = stockLevel(stock)
  const width = Math.min(100, Math.round((stock / MAX_BAR_STOCK) * 100))
  return (
    <div className="stock-meter" aria-label={`${stock} in stock`}>
      <div className={`stock-meter-fill stock-meter-fill--${level}`} style={{ width: `${width}%` }} />
    </div>
  )
}

function PanelHead({ title, meta }) {
  return (
    <div className="panel-head">
      <h2>{title}</h2>
      {meta && <span className="panel-meta">{meta}</span>}
    </div>
  )
}

function InventoryPanel({ items, loading, error }) {
  return (
    <section className="panel">
      <PanelHead title="Live inventory" meta={`${items.length} SKUs`} />
      {loading && <div className="skeleton-stack"><div /><div /><div /></div>}
      {error && <p className="error-box">{error}</p>}
      {!loading && !error && items.length === 0 && <p className="empty-state">No inventory records found.</p>}
      {!loading && !error && items.length > 0 && (
        <ul className="inventory-list">
          {items.map(item => {
            const level = stockLevel(item.stock)
            return (
              <li key={item.productId} className={`inventory-row inventory-row--${level}`}>
                <div className="inventory-main">
                  <strong>{item.name}</strong>
                  <span>{item.productId}</span>
                </div>
                <div className="inventory-stock-top">
                  <StockBar stock={item.stock} />
                  <strong className={`inventory-stock inventory-stock--${level}`}>
                    {String(item.stock).padStart(2, '0')}
                  </strong>
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
  const confirmed = result.status === 'CONFIRMED'
  return (
    <div className={`result-banner result-banner--${confirmed ? 'confirmed' : 'rejected'}`}>
      <strong>{confirmed ? 'Confirmed' : 'Rejected'}</strong>
      <span>{result.reason || (confirmed ? 'Inventory has been reserved.' : 'The order could not be completed.')}</span>
    </div>
  )
}

function CartPanel({ items, cart, addProductId, setAddProductId, addQuantity, setAddQuantity,
  onAddToCart, onUpdateQuantity, onRemove, onSubmit, submitting, formError, result, nameFor }) {
  const totalUnits = cart.reduce((sum, line) => sum + Number(line.quantity || 0), 0)

  return (
    <section className="panel">
      <PanelHead title="Place an order" />

      <div className="add-product">
        <select
          className="field-input"
          value={addProductId}
          onChange={e => setAddProductId(e.target.value)}
          disabled={items.length === 0}
        >
          {items.map(item => (
            <option key={item.productId} value={item.productId}>{item.name} · {item.stock} available</option>
          ))}
        </select>
        <input
          className="field-input"
          type="number"
          min="1"
          value={addQuantity}
          onChange={e => setAddQuantity(e.target.value)}
        />
        <button type="button" className="add-button" onClick={onAddToCart} disabled={!addProductId}>+ Add</button>
      </div>

      <div className="cart-head">
        <span>Order lines</span>
        <span>{totalUnits} {totalUnits === 1 ? 'unit' : 'units'}</span>
      </div>

      {cart.length === 0 ? (
        <p className="cart-empty">Your order is empty — add an item above.</p>
      ) : (
        <ul className="cart-list">
          {cart.map(line => (
            <li key={line.productId} className="cart-row">
              <div className="cart-product">
                <strong>{nameFor(line.productId)}</strong>
                <span>{line.productId}</span>
              </div>
              <div className="stepper">
                <button type="button" onClick={() => onUpdateQuantity(line.productId, Math.max(1, Number(line.quantity) - 1))}>−</button>
                <input type="number" min="1" value={line.quantity} onChange={e => onUpdateQuantity(line.productId, e.target.value)} />
                <button type="button" onClick={() => onUpdateQuantity(line.productId, Number(line.quantity) + 1)}>+</button>
              </div>
              <button className="remove-button" onClick={() => onRemove(line.productId)} aria-label={`Remove ${nameFor(line.productId)}`}>✕</button>
            </li>
          ))}
        </ul>
      )}

      {formError && <p className="error-box">{formError}</p>}

      <button className="submit-button" type="button" onClick={onSubmit} disabled={submitting || cart.length === 0}>
        {submitting ? 'Processing…' : 'Submit order'}
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
      <PanelHead title="Order history" meta={`${orders.length} total`} />
      {loading && <div className="skeleton-stack"><div /><div /><div /></div>}
      {error && <p className="error-box">{error}</p>}
      {!loading && !error && orders.length === 0 && <p className="empty-state">No orders have been placed yet.</p>}
      {!loading && !error && orders.length > 0 && (
        <ul className="order-list">
          {orders.slice().sort((a, b) => b.orderId - a.orderId).map(order => (
            <li key={order.orderId} className="order-row">
              <div className="order-row-main">
                <span className="order-number">#{String(order.orderId).padStart(4, '0')}</span>
                <span className={`badge ${statusClass(order.status)}`}>{order.status}</span>
              </div>
              <ul className="order-items">
                {order.items.map((line, idx) => (
                  <li key={idx}><span>{nameFor(line.productId)}</span><b>× {line.quantity}</b></li>
                ))}
              </ul>
              {order.reason && <p className="order-reason">{order.reason}</p>}
              {order.status === 'CONFIRMED' && (
                <button
                  type="button"
                  className="cancel-button"
                  onClick={() => onCancel(order.orderId)}
                  disabled={cancellingId === order.orderId}
                >
                  {cancellingId === order.orderId ? 'Cancelling…' : 'Cancel order'}
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
  const sorted = notifications.slice().sort((a, b) => b.notificationId - a.notificationId)
  return (
    <section className="panel">
      <PanelHead title="Activity feed" meta={`${notifications.length} events`} />
      {loading && <div className="skeleton-stack"><div /><div /><div /></div>}
      {error && <p className="error-box">{error}</p>}
      {!loading && !error && notifications.length === 0 && <p className="empty-state">No system activity yet.</p>}
      {!loading && !error && notifications.length > 0 && (
        <ul className="feed-list">
          {sorted.map((n, index) => (
            <li key={n.notificationId} className="feed-row">
              <div className="timeline">
                <span className="feed-dot" />
                {index < sorted.length - 1 && <span className="timeline-line" />}
              </div>
              <div className="feed-content">
                <span className="feed-time">EVENT {String(n.notificationId).padStart(3, '0')}</span>
                <p>{n.message}</p>
              </div>
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
    const map = new Map(items.map(i => [i.productId, i.name]))
    return productId => map.get(productId) || productId
  }, [items])

  async function loadInventory() {
    try {
      setLoadingInventory(true); setInventoryError('')
      const data = await fetchInventory()
      setItems(data)
      if (data.length > 0 && !addProductId) setAddProductId(data[0].productId)
    } catch (err) { setInventoryError(err.message) }
    finally { setLoadingInventory(false) }
  }

  async function loadOrders() {
    try { setLoadingOrders(true); setOrdersError(''); setOrders(await fetchOrders()) }
    catch (err) { setOrdersError(err.message) }
    finally { setLoadingOrders(false) }
  }

  async function loadNotifications() {
    try { setLoadingNotifications(true); setNotificationsError(''); setNotifications(await fetchNotifications()) }
    catch (err) { setNotificationsError(err.message) }
    finally { setLoadingNotifications(false) }
  }

  function loadAll() { loadInventory(); loadOrders(); loadNotifications() }

  useEffect(() => { loadAll() }, [])

  function handleAddToCart() {
    if (!addProductId) return
    const qty = Number(addQuantity)
    if (!qty || qty < 1) return
    setCart(prev => {
      const existing = prev.find(line => line.productId === addProductId)
      if (existing) return prev.map(line => line.productId === addProductId ? { ...line, quantity: line.quantity + qty } : line)
      return [...prev, { productId: addProductId, quantity: qty }]
    })
    setAddQuantity(1)
  }

  function handleUpdateQuantity(productId, value) {
    const qty = Number(value)
    if (!qty || qty < 1) return
    setCart(prev => prev.map(line => line.productId === productId ? { ...line, quantity: qty } : line))
  }

  function handleRemoveFromCart(productId) { setCart(prev => prev.filter(line => line.productId !== productId)) }

  async function handleSubmitOrder() {
    setFormError(''); setResult(null)
    if (!cart.length) return setFormError('Add at least one product to the order.')
    if (cart.some(line => !line.quantity || line.quantity < 1)) return setFormError('Every line needs a quantity of at least 1.')
    setSubmitting(true)
    try {
      const response = await placeOrder(cart)
      setResult(response); setCart([]); loadAll()
    } catch (err) { setFormError(err.message) }
    finally { setSubmitting(false) }
  }

  async function handleCancel(orderId) {
    setCancellingId(orderId)
    try { await cancelOrder(orderId); loadAll() }
    catch (err) { setOrdersError(err.message) }
    finally { setCancellingId(null) }
  }

  const lowStock = items.filter(i => i.stock > 0 && i.stock < LOW_STOCK_THRESHOLD).length
  const outStock = items.filter(i => i.stock === 0).length

  return (
    <div className="app-shell">
      <header className="topbar">
        <div>
          <div className="wordmark">Order<span>Hub</span></div>
          <p className="tagline">Inventory &amp; order console</p>
        </div>
        <div className="doc-stub">
          <div><span className="live-dot" /><strong>ONLINE</strong></div>
          <div>DOC NO. {String(orders.length + 1).padStart(6, '0')}</div>
          <div>TERMINAL WEB · {todayStub()}</div>
        </div>
      </header>

      <div className="barcode" aria-hidden="true" />

      <div className="summary-line">
        <span>SKUS <b>{items.length}</b></span>
        <span>ORDERS <b>{orders.length}</b></span>
        <span>LOW STOCK <b>{lowStock}</b></span>
        <span>OUT OF STOCK <b>{outStock}</b></span>
        <span>EVENTS <b>{notifications.length}</b></span>
      </div>

      <main className="dashboard-grid">
        <CartPanel
          items={items} cart={cart} addProductId={addProductId} setAddProductId={setAddProductId}
          addQuantity={addQuantity} setAddQuantity={setAddQuantity} onAddToCart={handleAddToCart}
          onUpdateQuantity={handleUpdateQuantity} onRemove={handleRemoveFromCart}
          onSubmit={handleSubmitOrder} submitting={submitting} formError={formError}
          result={result} nameFor={nameFor}
        />
        <InventoryPanel items={items} loading={loadingInventory} error={inventoryError} />
        <OrderHistoryPanel
          orders={orders} loading={loadingOrders} error={ordersError}
          onCancel={handleCancel} cancellingId={cancellingId} nameFor={nameFor}
        />
        <NotificationPanel notifications={notifications} loading={loadingNotifications} error={notificationsError} />
      </main>

      <footer className="footer">
        <span>ORDERHUB — INVENTORY + ORDER SERVICE</span>
        <span>MODULAR MONOLITH</span>
      </footer>
    </div>
  )
}
