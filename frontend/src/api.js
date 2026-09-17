const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

async function handle(res, fallbackMessage) {
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new Error(body?.message || body?.reason || fallbackMessage)
  }
  return res.json()
}

export async function fetchInventory() {
  const res = await fetch(`${API_URL}/api/inventory`)
  return handle(res, `Failed to load inventory (${res.status})`)
}

export async function fetchOrders() {
  const res = await fetch(`${API_URL}/api/orders`)
  return handle(res, `Failed to load order history (${res.status})`)
}

export async function fetchNotifications() {
  const res = await fetch(`${API_URL}/api/notifications`)
  return handle(res, `Failed to load notifications (${res.status})`)
}

export async function placeOrder(items) {
  const res = await fetch(`${API_URL}/api/orders`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ items }),
  })
  return handle(res, 'The order could not be submitted. Is the backend running?')
}

export async function cancelOrder(orderId) {
  const res = await fetch(`${API_URL}/api/orders/${orderId}/cancel`, {
    method: 'POST',
  })
  return handle(res, 'The order could not be cancelled.')
}
