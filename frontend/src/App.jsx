import { useEffect, useState } from 'react'
import { fetchInventory, placeOrder } from './api.js'

const MAX_BAR_STOCK = 30 // just for sizing the stock bar visually

function stockLevel(stock) {
  if (stock === 0) return 'out'
  if (stock <= 5) return 'low'
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
          {items.map((item) => (
            <li key={item.productId} className="inventory-row">
              <div className="inventory-row-top">
                <span className="inventory-name">{item.name}</span>
                <span className="inventory-id">{item.productId}</span>
              </div>
              <div className="inventory-row-bottom">
                <StockBar stock={item.stock} />
                <span className={`inventory-stock inventory-stock--${stockLevel(item.stock)}`}>
                  {item.stock} in stock
                </span>
              </div>
            </li>
          ))}
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

export default function App() {
  const [items, setItems] = useState([])
  const [loadingInventory, setLoadingInventory] = useState(true)
  const [inventoryError, setInventoryError] = useState('')

  const [productId, setProductId] = useState('')
  const [quantity, setQuantity] = useState(1)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState(null)
  const [formError, setFormError] = useState('')

  async function loadInventory() {
    try {
      setLoadingInventory(true)
      setInventoryError('')
      const data = await fetchInventory()
      setItems(data)
      if (data.length > 0 && !productId) {
        setProductId(data[0].productId)
      }
    } catch (err) {
      setInventoryError(err.message)
    } finally {
      setLoadingInventory(false)
    }
  }

  useEffect(() => {
    loadInventory()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function handleSubmit(e) {
    e.preventDefault()
    setFormError('')
    setResult(null)

    if (!productId) {
      setFormError('Choose a product first.')
      return
    }
    if (quantity < 1) {
      setFormError('Quantity has to be at least 1.')
      return
    }

    setSubmitting(true)
    try {
      const response = await placeOrder(productId, Number(quantity))
      setResult(response)
      await loadInventory() // reflect the new stock number immediately
    } catch (err) {
      setFormError(err.message)
    } finally {
      setSubmitting(false)
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
        <section className="panel">
          <h2 className="panel-title">Place an order</h2>
          <form className="order-form" onSubmit={handleSubmit}>
            <label className="field">
              <span className="field-label">Product</span>
              <select
                className="field-input"
                value={productId}
                onChange={(e) => setProductId(e.target.value)}
                disabled={loadingInventory || items.length === 0}
              >
                {items.map((item) => (
                  <option key={item.productId} value={item.productId}>
                    {item.name} ({item.productId})
                  </option>
                ))}
              </select>
            </label>

            <label className="field">
              <span className="field-label">Quantity</span>
              <input
                className="field-input"
                type="number"
                min="1"
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
              />
            </label>

            {formError && <p className="error-text">{formError}</p>}

            <button className="submit-btn" type="submit" disabled={submitting}>
              {submitting ? 'Submitting…' : 'Submit order'}
            </button>
          </form>

          <ResultStamp result={result} />
        </section>

        <InventoryPanel items={items} loading={loadingInventory} error={inventoryError} />
      </main>
    </div>
  )
}
