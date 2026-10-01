import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
  headers: { Accept: 'application/json' },
  withCredentials: true
})

const csrfClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
  headers: { Accept: 'application/json' },
  withCredentials: true
})

let csrfToken
let csrfTokenRequest

function getCsrfToken() {
  if (csrfToken) return Promise.resolve(csrfToken)
  if (!csrfTokenRequest) {
    csrfTokenRequest = csrfClient.get('/api/v1/auth/csrf')
      .then(({ data }) => {
        if (!data?.token || !data?.headerName) {
          throw new Error('The CSRF endpoint returned an invalid token response')
        }
        csrfToken = { headerName: data.headerName, token: data.token }
        return csrfToken
      })
      .finally(() => {
        csrfTokenRequest = undefined
      })
  }
  return csrfTokenRequest
}

function resetCsrfToken() {
  csrfToken = undefined
  csrfTokenRequest = undefined
}

api.interceptors.request.use(async (config) => {
  if (['post', 'put', 'patch', 'delete'].includes(config.method?.toLowerCase())) {
    const token = await getCsrfToken()
    config.headers[token.headerName] = token.token
  }
  return config
})

export const authApi = {
  login: (credentials) => api.post('/api/v1/auth/login', credentials).then(({ data }) => {
    resetCsrfToken()
    return data
  }),
  me: () => api.get('/api/v1/auth/me').then(({ data }) => data),
  logout: () => api.post('/api/v1/auth/logout').finally(resetCsrfToken)
}

export const adminApi = {
  overview: () => api.get('/api/v1/admin/overview').then(({ data }) => data),
  tags: (params = {}) => api.get('/api/v1/admin/tags', { params }).then(({ data }) => data),
  scans: () => api.get('/api/v1/admin/scans').then(({ data }) => data),
  scanActivity: () => api.get('/api/v1/admin/scans/activity').then(({ data }) => data),
  securityEvents: () => api.get('/api/v1/admin/security-events').then(({ data }) => data),
  locations: () => api.get('/api/v1/admin/scans/locations').then(({ data }) => data),
  revokeTag: (uid) => api.post(`/api/v1/admin/tags/${encodeURIComponent(uid)}/revoke`).then(({ data }) => data),
  activateTag: (uid) => api.post(`/api/v1/admin/tags/${encodeURIComponent(uid)}/activate`).then(({ data }) => data),
  deleteTag: (uid) => api.delete(`/api/v1/admin/tags/${encodeURIComponent(uid)}`),
  bulkTags: (uids, action) => api.post('/api/v1/admin/tags/bulk', { uids, action }).then(({ data }) => data),
  updateTagMetadata: (uid, metadata) => api.put(`/api/v1/admin/tags/${encodeURIComponent(uid)}/metadata`, metadata).then(({ data }) => data),
  products: () => api.get('/api/v1/admin/products').then(({ data }) => data),
  createProduct: (product) => api.post('/api/v1/admin/products', product).then(({ data }) => data),
  deleteProduct: (id) => api.delete(`/api/v1/admin/products/${encodeURIComponent(id)}`),
  importProvisioning: (file) => {
    const form = new FormData()
    form.append('file', file)
    return api.post('/api/v1/admin/provisioning/import', form).then(({ data }) => data)
  },
  exportProvisioningKeys: (batchId) => api.get(
    `/api/v1/admin/provisioning/${encodeURIComponent(batchId)}/keys.csv`,
    { responseType: 'blob' }
  ).then(({ data }) => data),
  accounts: () => api.get('/api/v1/admin/accounts').then(({ data }) => data),
  createAccount: (account) => api.post('/api/v1/admin/accounts', account).then(({ data }) => data),
  deleteAccount: (id) => api.delete(`/api/v1/admin/accounts/${encodeURIComponent(id)}`)
}

export default api
