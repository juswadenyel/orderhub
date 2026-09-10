const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8081'

export async function fetchInventory() {
  const res = await fetch(`${API_URL}/api/inventory`)
  if (!res.ok) {
    throw new Error(`Failed to load inventory (${res.status})`)
  }
  return res.json()
}

export async function placeOrder(productId, quantity) {
  const res = await fetch(`${API_URL}/api/orders`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ productId, quantity }),
  })

  const data = await res.json().catch(() => null)

  if (!res.ok || !data) {
    throw new Error('The order could not be submitted. Is the backend running?')
  }

  return data
}
