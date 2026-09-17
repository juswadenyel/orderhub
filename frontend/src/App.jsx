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

function Icon({ name, size = 18 }) {
  const paths = {
    box: <><path d="m3 7 9-4 9 4-9 4-9-4Z"/><path d="M3 7v10l9 4 9-4V7"/><path d="M12 11v10"/></>,
    cart: <><circle cx="9" cy="20" r="1"/><circle cx="18" cy="20" r="1"/><path d="M3 4h2l2.2 11h11.3l2-8H6"/></>,
    activity: <><path d="M3 12h4l2-7 4 14 2-7h6"/></>,
    arrow: <><path d="M5 12h14"/><path d="m13 6 6 6-6 6"/></>,
    plus: <><path d="M12 5v14M5 12h14"/></>,
    trash: <><path d="M4 7h16"/><path d="M10 11v6M14 11v6"/><path d="M6 7l1 13h10l1-13M9 7V4h6v3"/></>,
    refresh: <><path d="M20 11a8 8 0 0 0-14.9-3"/><path d="M4 4v5h5"/><path d="M4 13a8 8 0 0 0 14.9 3"/><path d="M20 20v-5h-5"/></>,
    check: <path d="m5 12 4 4L19 6"/>,
    x: <><path d="M6 6l12 12M18 6 6 18"/></>,
    clock: <><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></>,
    layers: <><path d="m12 3 9 5-9 5-9-5 9-5Z"/><path d="m3 12 9 5 9-5"/><path d="m3 16 9 5 9-5"/></>,
  }
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"
      aria-hidden="true">{paths[name]}</svg>
  )
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

function SectionHeader({ icon, eyebrow, title, meta, onRefresh }) {
  return (
    <div className="section-head">
      <div className="section-heading">
        <div className="section-icon"><Icon name={icon} size={17} /></div>
        <div>
          <span className="eyebrow">{eyebrow}</span>
          <h2>{title}</h2>
        </div>
      </div>
      {onRefresh ? (
        <button className="icon-button" onClick={onRefresh} aria-label="Refresh">
          <Icon name="refresh" size={16} />
        </button>
      ) : meta ? <span className="section-meta">{meta}</span> : null}
    </div>
  )
}

function StatCard({ icon, label, value, detail }) {
  return (
    <div className="stat-card">
      <div className="stat-icon"><Icon name={icon} size={18} /></div>
      <div>
        <span className="stat-label">{label}</span>
        <strong>{value}</strong>
        <span className="stat-detail">{detail}</span>
      </div>
    </div>
  )
}

function InventoryPanel({ items, loading, error }) {
  const low = items.filter(i => i.stock > 0 && i.stock < LOW_STOCK_THRESHOLD).length
  const out = items.filter(i => i.stock === 0).length

  return (
    <section className="panel inventory-panel">
      <SectionHeader icon="box" eyebrow="Warehouse" title="Live inventory" meta={`${items.length} SKUs`} />
      <div className="inventory-summary">
        <span><i className="status-dot status-dot--ok" />Healthy</span>
        <span><i className="status-dot status-dot--low" />{low} low</span>
        <span><i className="status-dot status-dot--out" />{out} out</span>
      </div>
      {loading && <div className="skeleton-stack"><div/><div/><div/></div>}
      {error && <p className="error-box">{error}</p>}
      {!loading && !error && items.length === 0 && <p className="empty-state">No inventory records found.</p>}
      {!loading && !error && items.length > 0 && (
        <ul className="inventory-list">
          {items.map(item => {
            const level = stockLevel(item.stock)
            return (
              <li key={item.productId} className={`inventory-row inventory-row--${level}`}>
                <div className="inventory-main">
                  <div className="product-avatar">{item.name?.slice(0, 1).toUpperCase()}</div>
                  <div className="inventory-copy">
                    <strong>{item.name}</strong>
                    <span>{item.productId}</span>
                  </div>
                </div>
                <div className="inventory-stock-wrap">
                  <div className="inventory-stock-top">
                    <StockBar stock={item.stock} />
                    <strong className={`inventory-stock inventory-stock--${level}`}>{item.stock}</strong>
                  </div>
                  <span className="stock-caption">{level === 'out' ? 'Out of stock' : level === 'low' ? 'Running low' : 'Units available'}</span>
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
      <div className="result-icon"><Icon name={confirmed ? 'check' : 'x'} size={18} /></div>
      <div>
        <strong>{confirmed ? 'Order confirmed' : 'Order rejected'}</strong>
        <span>{result.reason || (confirmed ? 'Inventory has been reserved successfully.' : 'The order could not be completed.')}</span>
      </div>
    </div>
  )
}

function CartPanel({ items, cart, addProductId, setAddProductId, addQuantity, setAddQuantity,
  onAddToCart, onUpdateQuantity, onRemove, onSubmit, submitting, formError, result, nameFor }) {
  const totalUnits = cart.reduce((sum, line) => sum + Number(line.quantity || 0), 0)

  return (
    <section className="panel order-panel">
      <div className="order-hero">
        <div>
          <span className="eyebrow">New transaction</span>
          <h2>Build an order</h2>
          <p>Select inventory, set quantities, and send it straight to the order service.</p>
        </div>
        <div className="order-orb"><Icon name="cart" size={24} /></div>
      </div>

      <div className="add-product">
        <div className="select-wrap">
          <label>Product</label>
          <select value={addProductId} onChange={e => setAddProductId(e.target.value)} disabled={items.length === 0}>
            {items.map(item => (
              <option key={item.productId} value={item.productId}>{item.name} · {item.stock} available</option>
            ))}
          </select>
        </div>
        <div className="quantity-wrap">
          <label>Qty</label>
          <input type="number" min="1" value={addQuantity} onChange={e => setAddQuantity(e.target.value)} />
        </div>
        <button type="button" className="add-button" onClick={onAddToCart} disabled={!addProductId}>
          <Icon name="plus" size={17} /> Add
        </button>
      </div>

      <div className="cart-head">
        <span>Order lines</span>
        <span>{totalUnits} {totalUnits === 1 ? 'unit' : 'units'}</span>
      </div>

      {cart.length === 0 ? (
        <div className="cart-empty">
          <div className="empty-cart-icon"><Icon name="cart" size={22} /></div>
          <strong>Your order is empty</strong>
          <span>Add an item above to begin.</span>
        </div>
      ) : (
        <ul className="cart-list">
          {cart.map(line => (
            <li key={line.productId} className="cart-row">
              <div className="cart-product-avatar">{nameFor(line.productId).slice(0, 1).toUpperCase()}</div>
              <div className="cart-product">
                <strong>{nameFor(line.productId)}</strong>
                <span>{line.productId}</span>
              </div>
              <div className="stepper">
                <button onClick={() => onUpdateQuantity(line.productId, Math.max(1, Number(line.quantity) - 1))}>−</button>
                <input type="number" min="1" value={line.quantity} onChange={e => onUpdateQuantity(line.productId, e.target.value)} />
                <button onClick={() => onUpdateQuantity(line.productId, Number(line.quantity) + 1)}>+</button>
              </div>
              <button className="remove-button" onClick={() => onRemove(line.productId)} aria-label={`Remove ${nameFor(line.productId)}`}>
                <Icon name="trash" size={16} />
              </button>
            </li>
          ))}
        </ul>
      )}

      {formError && <p className="error-box">{formError}</p>}

      <button className="submit-button" type="button" onClick={onSubmit} disabled={submitting || cart.length === 0}>
        <span>{submitting ? 'Processing order…' : 'Submit order'}</span>
        {!submitting && <Icon name="arrow" size={17} />}
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
      <SectionHeader icon="layers" eyebrow="Transactions" title="Order history" meta={`${orders.length} total`} />
      {loading && <div className="skeleton-stack"><div/><div/><div/></div>}
      {error && <p className="error-box">{error}</p>}
      {!loading && !error && orders.length === 0 && <div className="empty-state">No orders have been placed yet.</div>}
      {!loading && !error && orders.length > 0 && (
        <ul className="order-list">
          {orders.slice().sort((a, b) => b.orderId - a.orderId).map(order => (
            <li key={order.orderId} className="order-row">
              <div className="order-row-main">
                <div className="order-number">
                  <span>ORDER</span>
                  <strong>#{String(order.orderId).padStart(4, '0')}</strong>
                </div>
                <span className={`badge ${statusClass(order.status)}`}>
                  <i />{order.status}
                </span>
              </div>
              <ul className="order-items">
                {order.items.map((line, idx) => <li key={idx}><span>{nameFor(line.productId)}</span><b>× {line.quantity}</b></li>)}
              </ul>
              {order.reason && <p className="order-reason">{order.reason}</p>}
              {order.status === 'CONFIRMED' && (
                <button type="button" className="cancel-button" onClick={() => onCancel(order.orderId)} disabled={cancellingId === order.orderId}>
                  <Icon name="x" size={14} /> {cancellingId === order.orderId ? 'Cancelling…' : 'Cancel order'}
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
    <section className="panel activity-panel">
      <SectionHeader icon="activity" eyebrow="System events" title="Activity feed" meta={`${notifications.length} events`} />
      {loading && <div className="skeleton-stack"><div/><div/><div/></div>}
      {error && <p className="error-box">{error}</p>}
      {!loading && !error && notifications.length === 0 && <div className="empty-state">No system activity yet.</div>}
      {!loading && !error && notifications.length > 0 && (
        <ul className="feed-list">
          {notifications.slice().sort((a, b) => b.notificationId - a.notificationId).map((n, index) => (
            <li key={n.notificationId} className="feed-row">
              <div className="timeline">
                <span className="feed-dot" />
                {index < notifications.length - 1 && <span className="timeline-line" />}
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
  const confirmed = orders.filter(o => o.status === 'CONFIRMED').length

  return (
    <div className="app-shell">
      <div className="ambient ambient-one" />
      <div className="ambient ambient-two" />

      <header className="topbar">
        <div className="brand">
          <div className="brand-mark"><span>O</span></div>
          <div><strong>Order<span>Hub</span></strong><small>OPERATIONS CONSOLE</small></div>
        </div>
        <div className="topbar-right">
          <div className="live-status"><i /> Systems operational</div>
          <button className="refresh-button" onClick={loadAll}><Icon name="refresh" size={15} /> Refresh</button>
        </div>
      </header>

      <main className="dashboard">
        <section className="hero">
          <div>
            <span className="eyebrow hero-eyebrow">Order management / Command center</span>
            <h1>Move inventory.<br /><em>Move business.</em></h1>
            <p>A focused workspace for creating orders, watching stock, and tracking every system event in one place.</p>
          </div>
          <div className="hero-grid">
            <div className="hero-grid-line" />
            <span>REAL-TIME<br />OPERATIONS</span>
          </div>
        </section>

        <section className="stats">
          <StatCard icon="box" label="SKUs tracked" value={items.length} detail="Across inventory" />
          <StatCard icon="layers" label="Orders placed" value={orders.length} detail={`${confirmed} confirmed`} />
          <StatCard icon="clock" label="Low stock" value={lowStock} detail={`${outStock} currently unavailable`} />
          <StatCard icon="activity" label="System events" value={notifications.length} detail="Latest activity" />
        </section>

        <div className="dashboard-grid">
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
        </div>
      </main>

      <footer className="footer">
        <span>ORDERHUB <b>•</b> INVENTORY + ORDER SERVICE</span>
        <span>MODULAR MONOLITH <b>•</b> ONLINE</span>
      </footer>
    </div>
  )
}
