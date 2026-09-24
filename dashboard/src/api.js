import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
  headers: { Accept: 'application/json' }
})

export const adminApi = {
  overview: () => api.get('/api/v1/admin/overview').then(({ data }) => data),
  tags: (params = {}) => api.get('/api/v1/admin/tags', { params }).then(({ data }) => data),
  scans: () => api.get('/api/v1/admin/scans').then(({ data }) => data),
  securityEvents: () => api.get('/api/v1/admin/security-events').then(({ data }) => data),
  locations: () => api.get('/api/v1/admin/scans/locations').then(({ data }) => data),
  revokeTag: (uid) => api.post(`/api/v1/admin/tags/${encodeURIComponent(uid)}/revoke`).then(({ data }) => data),
  activateTag: (uid) => api.post(`/api/v1/admin/tags/${encodeURIComponent(uid)}/activate`).then(({ data }) => data),
  updateTagMetadata: (uid, metadata) => api.put(`/api/v1/admin/tags/${encodeURIComponent(uid)}/metadata`, metadata).then(({ data }) => data)
  ,products: () => api.get('/api/v1/admin/products').then(({ data }) => data),
  createProduct: (product) => api.post('/api/v1/admin/products', product).then(({ data }) => data),
  importProvisioning: (file) => {
    const form = new FormData()
    form.append('file', file)
    return api.post('/api/v1/admin/provisioning/import', form).then(({ data }) => data)
  }
}

export default api
