import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
  headers: { Accept: 'application/json' },
  withCredentials: true
})

export const authApi = {
  login: (credentials) => api.post('/api/v1/auth/login', credentials).then(({ data }) => data),
  me: () => api.get('/api/v1/auth/me').then(({ data }) => data),
  logout: () => api.post('/api/v1/auth/logout')
}

export const adminApi = {
  overview: () => api.get('/api/v1/admin/overview').then(({ data }) => data),
  tags: (params = {}) => api.get('/api/v1/admin/tags', { params }).then(({ data }) => data),
  scans: () => api.get('/api/v1/admin/scans').then(({ data }) => data),
  securityEvents: () => api.get('/api/v1/admin/security-events').then(({ data }) => data),
  locations: () => api.get('/api/v1/admin/scans/locations').then(({ data }) => data),
  revokeTag: (uid) => api.post(`/api/v1/admin/tags/${encodeURIComponent(uid)}/revoke`).then(({ data }) => data),
  activateTag: (uid) => api.post(`/api/v1/admin/tags/${encodeURIComponent(uid)}/activate`).then(({ data }) => data),
  deleteTag: (uid) => api.delete(`/api/v1/admin/tags/${encodeURIComponent(uid)}`),
  updateTagMetadata: (uid, metadata) => api.put(`/api/v1/admin/tags/${encodeURIComponent(uid)}/metadata`, metadata).then(({ data }) => data)
  ,products: () => api.get('/api/v1/admin/products').then(({ data }) => data),
  createProduct: (product) => api.post('/api/v1/admin/products', product).then(({ data }) => data),
  deleteProduct: (id) => api.delete(`/api/v1/admin/products/${encodeURIComponent(id)}`),
  importProvisioning: (file) => {
    const form = new FormData()
    form.append('file', file)
    return api.post('/api/v1/admin/provisioning/import', form).then(({ data }) => data)
  },
  accounts: () => api.get('/api/v1/admin/accounts').then(({ data }) => data),
  createAccount: (account) => api.post('/api/v1/admin/accounts', account).then(({ data }) => data),
  deleteAccount: (id) => api.delete(`/api/v1/admin/accounts/${encodeURIComponent(id)}`)
}

export default api
