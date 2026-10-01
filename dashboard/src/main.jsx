import React, { useEffect, useMemo, useRef, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { CircleMarker, MapContainer, Popup, TileLayer, useMap } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'
import { adminApi, authApi } from './api'
import './styles.css'

const navItems = [
  { id: 'home', label: 'Overview', icon: '⌂' },
  { id: 'tags', label: 'Tag registry', icon: '⌁' },
  { id: 'provisioning', label: 'Provisioning', icon: '+', operatorOnly: true },
  { id: 'security', label: 'Security events', icon: '◈' },
  { id: 'accounts', label: 'Accounts', icon: '◎', adminOnly: true }
]

const number = (value) => new Intl.NumberFormat().format(value || 0)
const timeAgo = (value) => {
  if (!value) return '—'
  const minutes = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 60000))
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes}m ago`
  if (minutes < 1440) return `${Math.floor(minutes / 60)}h ago`
  return `${Math.floor(minutes / 1440)}d ago`
}
const exactTime = (value) => value
  ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(value))
  : '—'
const uniqueScans = (rows) => {
  const seen = new Set()
  return rows.filter((row) => {
    const key = `${row.uid}-${row.result}-${row.receivedCounter ?? 'none'}`
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

function App() {
  const [user, setUser] = useState(undefined)
  const [credentials, setCredentials] = useState({ username: '', password: '' })
  const [loginError, setLoginError] = useState('')
  const [page, setPage] = useState(window.location.hash.slice(1) || 'home')
  const [mobileNav, setMobileNav] = useState(false)

  useEffect(() => {
    const onHash = () => setPage(window.location.hash.slice(1) || 'home')
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  }, [])

  useEffect(() => { authApi.me().then(setUser).catch(() => setUser(null)) }, [])
  const login = async (event) => {
    event.preventDefault()
    try { setUser(await authApi.login(credentials)); setLoginError('') }
    catch { setLoginError('Invalid username or password') }
  }
  if (user === undefined) return null
  if (!user) return <main className="login-shell"><form className="panel login-panel" onSubmit={login}>
    <img className="login-logo" src="/authentichain-logo.svg" alt="Authentichain" />
    <div className="login-kicker">CONTROL CENTER</div><h1>Sign in securely</h1><p className="muted">Manage product identity, provisioning, and verification events.</p>
    <label>Username<input required value={credentials.username} onChange={e => setCredentials({ ...credentials, username: e.target.value })} /></label>
    <label>Password<input required type="password" value={credentials.password} onChange={e => setCredentials({ ...credentials, password: e.target.value })} /></label>
    {loginError && <div className="error-banner">{loginError}</div>}<button className="primary-button" type="submit">Sign in</button>
  </form></main>

  const navigate = (next) => {
    window.location.hash = next
    setMobileNav(false)
  }
  const canManage = user.role === 'ADMIN' || user.role === 'OPERATOR'

  return (
    <div className={`app-shell ${user.role === 'VIEWER' ? 'viewer-shell' : ''}`}>
      <aside className={`sidebar ${mobileNav ? 'sidebar-open' : ''}`}>
        <div className="brand"><img src="/authentichain-mark.svg" alt="" /><span>AUTHENTI<span>CHAIN</span></span></div>
        <div className="workspace-label">CONTROL CENTER</div>
        <nav>
          {navItems.filter((item) => (!item.adminOnly || user.role === 'ADMIN')
            && (!item.operatorOnly || canManage)).map((item) => (
            <button className={`nav-item ${page === item.id ? 'active' : ''}`} key={item.id} onClick={() => navigate(item.id)}>
              <span className="nav-icon">{item.icon}</span>{item.label}
            </button>
          ))}
        </nav>
        <div className="sidebar-footer">
          <span className="status-dot" /> API connection active
          <small>v1 · PostgreSQL</small>
        </div>
      </aside>
      {mobileNav && <button className="nav-backdrop" aria-label="Close navigation" onClick={() => setMobileNav(false)} />}
      <main className="main-content">
        <header className="topbar">
          <button className="menu-button" onClick={() => setMobileNav(true)} aria-label="Open navigation">☰</button>
          <div><div className="eyebrow">ADMINISTRATION</div><h1>{navItems.find((item) => item.id === page)?.label || 'Overview'}</h1></div>
          <div className="topbar-meta"><span className="user-role">{user.role === 'VIEWER' ? 'VISITOR · READ ONLY' : user.role}</span><span className="live-pill"><span className="status-dot" /> LIVE</span><span className="avatar">{user.username.slice(0, 2).toUpperCase()}</span><button className="text-button" onClick={() => authApi.logout().then(() => setUser(null))}>Log out</button></div>
        </header>
        <div className="page-content">
          {page === 'tags' ? <TagsPage readOnly={!canManage} /> : page === 'provisioning' && canManage ? <ProvisioningPage isAdmin={user.role === 'ADMIN'} /> : page === 'security' ? <SecurityPage /> : page === 'accounts' && user.role === 'ADMIN' ? <AccountsPage user={user} /> : <HomePage navigate={navigate} />}
        </div>
      </main>
    </div>
  )
}

function AccountsPage({ user }) {
  const [accounts, setAccounts] = useState([])
  const [form, setForm] = useState({ username: '', password: '', role: 'OPERATOR' })
  const [error, setError] = useState('')
  const load = async () => { try { setAccounts(await adminApi.accounts()); setError('') } catch { setError('Could not load accounts.') } }
  useEffect(() => { load() }, [])
  const create = async (event) => { event.preventDefault(); try { await adminApi.createAccount(form); setForm({ username: '', password: '', role: 'OPERATOR' }); await load() } catch { setError('Could not create account. Use a unique username and a password of at least 12 characters.') } }
  const remove = async (account) => { if (!window.confirm(`Delete account "${account.username}"?`)) return; try { await adminApi.deleteAccount(account.id); await load() } catch { setError('The account could not be deleted.') } }
  return <div className="stack"><div className="page-intro"><p className="muted">Create separate accounts and assign the least privilege needed.</p></div>{error && <div className="error-banner">{error}</div>}<div className="content-grid"><section className="panel"><div className="panel-heading"><div><h2>Create account</h2><span className="muted">Passwords are stored as BCrypt hashes.</span></div></div><form onSubmit={create} className="stack"><label>Username<input required value={form.username} onChange={(event) => setForm({ ...form, username: event.target.value })} /></label><label>Temporary password<input required minLength="12" type="password" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} /></label><label>Role<select value={form.role} onChange={(event) => setForm({ ...form, role: event.target.value })}><option value="VIEWER">Viewer — read-only</option><option value="OPERATOR">Operator — manage tags and provisioning</option><option value="ADMIN">Admin — manage accounts and everything else</option></select></label><button className="primary-button" type="submit">Create account</button></form></section><section className="panel"><div className="panel-heading"><div><h2>Accounts</h2><span className="muted">{accounts.length} accounts</span></div></div><div className="table-wrap"><table><thead><tr><th>USERNAME</th><th>ROLE</th><th /></tr></thead><tbody>{accounts.map((account) => <tr key={account.id}><td>{account.username}{account.username === user.username && <small className="table-subtitle">Current account</small>}</td><td><span className="badge">{account.role}</span></td><td className="actions">{account.username !== user.username && <button className="danger-button" onClick={() => remove(account)}>Delete</button>}</td></tr>)}</tbody></table></div></section></div></div>
}

function StatCard({ label, value, detail, accent }) {
  return <div className="stat-card"><div className={`stat-accent ${accent || ''}`} /><div className="stat-label">{label}</div><div className="stat-value">{value}</div><div className="stat-detail">{detail}</div></div>
}

function HomePage({ navigate }) {
  const [overview, setOverview] = useState(null)
  const [scans, setScans] = useState([])
  const [locations, setLocations] = useState([])
  const [activity, setActivity] = useState([])
  const [error, setError] = useState('')
  const [refreshing, setRefreshing] = useState(false)

  const load = async () => {
    try {
      const [summary, recent, points, dailyActivity] = await Promise.all([
        adminApi.overview(), adminApi.scans(), adminApi.locations(), adminApi.scanActivity()
      ])
      setOverview(summary)
      setScans(uniqueScans(recent || []))
      setLocations((points || []).filter((point) => Number.isFinite(point.latitude)
        && Number.isFinite(point.longitude) && Math.abs(point.latitude) <= 90
        && Math.abs(point.longitude) <= 180))
      setActivity(dailyActivity || [])
      setError('')
    } catch { setError('Unable to reach the API. Check VITE_API_URL and that the backend is running.') }
  }
  const refreshNow = async () => {
    setRefreshing(true)
    try { await load() } finally { setRefreshing(false) }
  }
  useEffect(() => { load(); const timer = setInterval(load, 15000); return () => clearInterval(timer) }, [])
  const successRate = overview?.successRate ?? 0

  return <div className="stack">
    <div className="page-intro"><div><p className="muted">A clear view of your physical product identity network.</p></div><div className="page-actions"><span className="refresh-label">Auto-refreshes every 15s</span><button className="outline-button" onClick={refreshNow} disabled={refreshing}>{refreshing ? 'Refreshing…' : '↻ Refresh now'}</button></div></div>
    {error && <div className="error-banner">{error}</div>}
    <div className="stats-grid">
      <StatCard label="ACTIVE TAGS" value={number(overview?.activeTags)} detail={`${number(overview?.totalTags)} registered total`} accent="purple" />
      <StatCard label="SCANS · LAST 24H" value={number(overview?.scansLast24Hours)} detail={`${number(overview?.successfulScansLast24Hours)} verified successfully`} accent="blue" />
      <StatCard label="SUCCESS RATE" value={`${Number(successRate).toFixed(1)}%`} detail="of scans verified as real" accent="green" />
    </div>
    <div className="content-grid">
      <section className="panel chart-panel"><div className="panel-heading"><div><h2>Scan activity</h2><span className="muted">Daily attempts · last 7 days (UTC)</span></div><button className="text-button" onClick={() => navigate('security')}>View security →</button></div>
        <ActivityChart rows={activity} />
      </section>
      <section className="panel"><div className="panel-heading"><div><h2>Verified scan locations</h2><span className="muted">{number(locations.length)} recent locations · GPS when shared, otherwise approximate IP</span></div></div>
        {locations.length
          ? <LocationMap locations={locations} />
          : <div className="location-map-empty">No verified scans with shared GPS or GeoIP coordinates yet.</div>}
        <p className="map-caption-note">Device GPS is optional, user-shared, and may be inaccurate or spoofed. IP-based locations are approximate. Tile requests go to OpenStreetMap.</p>
        <a className="geoip-attribution" href="https://www.maxmind.com/" target="_blank" rel="noreferrer">This product includes GeoLite Data created by MaxMind, available from MaxMind.</a>
      </section>
    </div>
    <section className="panel"><div className="panel-heading"><div><h2>Latest scans</h2><span className="muted">The most recent activity across your tags</span></div><button className="text-button" onClick={() => navigate('security')}>All activity →</button></div>
      <ScanTable rows={scans.slice(0, 6)} security />
    </section>
  </div>
}

function ActivityChart({ rows }) {
  if (!rows.length) return <div className="empty-state">No activity summary is available yet.</div>
  const maxTotal = Math.max(...rows.map((row) => row.total), 1)
  const verifiedTotal = rows.reduce((total, row) => total + row.verified, 0)
  const flaggedTotal = rows.reduce((total, row) => total + row.flagged, 0)
  return <>
    <div className="activity-totals"><span><strong>{number(verifiedTotal)}</strong> verified</span><span><strong>{number(flaggedTotal)}</strong> flagged</span></div>
    <div className="activity-chart" role="img" aria-label="Daily verified and flagged scan counts for the last seven days">
      {rows.map((row) => {
        const totalHeight = row.total ? Math.max(4, row.total / maxTotal * 100) : 0
        const day = new Date(`${row.day}T00:00:00Z`).toLocaleDateString(undefined, { weekday: 'short', timeZone: 'UTC' })
        return <div className="bar-group" key={row.day}>
          <span className="bar-count">{row.total || ''}</span>
          <div className="bar-track">
            <div className="bar-stack" style={{ height: `${totalHeight}%` }} title={`${row.day}: ${row.total} scans`}>
              <span className="bar-segment flagged" style={{ height: `${row.total ? row.flagged / row.total * 100 : 0}%` }} />
              <span className="bar-segment verified" style={{ height: `${row.total ? row.verified / row.total * 100 : 0}%` }} />
            </div>
          </div>
          <span>{day}</span>
        </div>
      })}
    </div>
    <div className="chart-legend"><span><i className="legend-dot verified" /> Verified</span><span><i className="legend-dot flagged" /> Flagged</span></div>
  </>
}

function LocationMap({ locations }) {
  return <MapContainer center={[20, 0]} zoom={2} scrollWheelZoom={false} className="location-map">
    <TileLayer
      attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
      url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
    />
    <MapAutoFit locations={locations} />
    {locations.map((point, index) => <CircleMarker
      key={`${point.latitude}-${point.longitude}-${index}`}
      center={[point.latitude, point.longitude]}
      radius={7}
      pathOptions={{ color: '#ffffff', weight: 2, fillColor: '#087f6b', fillOpacity: 0.85 }}
    >
      <Popup>
        <strong>{point.source === 'DEVICE_GPS'
          ? 'User-shared device GPS (unverified)'
          : point.source === 'CLIENT_COORDINATES'
            ? 'Client-supplied coordinates (untrusted)'
            : 'GeoLite2 City · approximate IP location'}</strong><br />
        {point.latitude.toFixed(point.source === 'GEOIP' ? 3 : 5)}, {point.longitude.toFixed(point.source === 'GEOIP' ? 3 : 5)}
        {point.timestamp && <><br />{exactTime(point.timestamp)}</>}
      </Popup>
    </CircleMarker>)}
  </MapContainer>
}

function MapAutoFit({ locations }) {
  const map = useMap()
  const previousLocations = useRef('')
  const locationSignature = locations.map((point) => `${point.latitude},${point.longitude}`).join(';')
  useEffect(() => {
    if (locationSignature === previousLocations.current) return
    previousLocations.current = locationSignature
    if (locations.length === 1) {
      map.setView([locations[0].latitude, locations[0].longitude], 5)
    } else if (locations.length > 1) {
      map.fitBounds(locations.map((point) => [point.latitude, point.longitude]), {
        padding: [20, 20],
        maxZoom: 5
      })
    }
  }, [locations, locationSignature, map])
  return null
}

function ScanTable({ rows, security = false }) {
  if (!rows.length) return <div className="empty-state">No scan activity recorded yet.</div>
  const visibleRows = security ? rows : uniqueScans(rows)
  return <div className="table-wrap"><table className="scan-table"><thead><tr><th>TAG UID</th><th>TIME</th><th>RESULT</th><th>RECEIVED CTR</th><th>NEXT ACCEPTED CTR</th>{security && <><th>LOCATION EVIDENCE</th><th>IP</th></>}</tr></thead><tbody>{visibleRows.map((row, index) => {
    const geoPlace = [row.geoCity, row.geoRegion, row.geoCountry].filter(Boolean).join(', ')
    const geoCoordinates = row.geoLatitude != null && row.geoLongitude != null
      ? `${Number(row.geoLatitude).toFixed(3)}, ${Number(row.geoLongitude).toFixed(3)}`
      : ''
    const deviceCoordinates = row.deviceLatitude != null && row.deviceLongitude != null
      ? `${Number(row.deviceLatitude).toFixed(5)}, ${Number(row.deviceLongitude).toFixed(5)}`
      : ''
    const exactLocationExists = row.deviceLatitude != null || row.deviceLongitude != null
      || row.latitude != null || row.longitude != null
    const clientCoordinates = row.latitude != null && row.longitude != null
      ? `${Number(row.latitude).toFixed(5)}, ${Number(row.longitude).toFixed(5)}`
      : ''
    const locationEvidence = deviceCoordinates
      ? `User-shared device GPS (unverified): ${deviceCoordinates}`
      : clientCoordinates
        ? `Client-supplied coordinates (untrusted): ${clientCoordinates}`
        : exactLocationExists
          ? 'Precise location evidence supplied (incomplete)'
          : geoPlace
            ? `GeoLite2 City · approximate IP location: ${geoPlace}${row.geoCountryIsoCode ? ` (${row.geoCountryIsoCode})` : ''}${geoCoordinates ? ` · ${geoCoordinates}` : ''}`
        : 'No GPS or GeoIP location'
    return <tr key={row.id || `${row.uid}-${row.timestamp}-${index}`}><td data-label="TAG UID"><code>{row.uid || '—'}</code></td><td data-label="TIME"><time className="scan-time" dateTime={row.timestamp || undefined} title={exactTime(row.timestamp)}><span>{timeAgo(row.timestamp)}</span><small>{exactTime(row.timestamp)}</small></time></td><td data-label="RESULT"><span className={`badge ${String(row.result || '').toLowerCase()}`}>{row.result || 'UNKNOWN'}</span></td><td data-label="RECEIVED CTR">{row.receivedCounter == null ? '—' : number(row.receivedCounter)}</td><td data-label="NEXT ACCEPTED CTR">{row.expectedCounter == null ? '—' : number(row.expectedCounter)}</td>{security && <><td data-label="LOCATION">{locationEvidence}</td><td data-label="IP"><code>{row.ipAddress || '—'}</code></td></>}</tr>
  })}</tbody></table></div>
}

function TagsPage({ readOnly = false }) {
  const [tags, setTags] = useState([])
  const [products, setProducts] = useState([])
  const [filters, setFilters] = useState({ q: '', productId: '', manufacturer: '', status: '' })
  const [pageIndex, setPageIndex] = useState(0)
  const [pageInfo, setPageInfo] = useState({ totalElements: 0, totalPages: 0 })
  const [selected, setSelected] = useState(() => new Set())
  const [loading, setLoading] = useState(true)
  const [processing, setProcessing] = useState(false)
  const [error, setError] = useState('')
  const [feedback, setFeedback] = useState('')
  const [editing, setEditing] = useState(null)
  const pageSize = 25
  const load = async () => {
    setLoading(true)
    try {
      const params = { page: pageIndex, size: pageSize }
      if (filters.q.trim()) params.q = filters.q.trim()
      if (filters.productId) params.productId = filters.productId
      if (filters.manufacturer) params.manufacturer = filters.manufacturer
      if (filters.status) params.status = filters.status
      const result = await adminApi.tags(params)
      const rows = Array.isArray(result) ? result : result.content || []
      setTags(rows)
      setPageInfo({
        totalElements: result.totalElements ?? rows.length,
        totalPages: result.totalPages ?? (rows.length < pageSize ? pageIndex + 1 : pageIndex + 2)
      })
      setError('')
    } catch (requestError) {
      const status = requestError?.response?.status
      if (status === 401 || status === 403) {
        setError('Tag registry access denied. Sign in again with an ADMIN, OPERATOR, or VIEWER account.')
      } else if (status) {
        setError(`Could not load the tag registry (API returned HTTP ${status}).`)
      } else {
        setError('Could not reach the API while loading the tag registry. Check the connection and backend status.')
      }
    } finally {
      setLoading(false)
    }
  }
  useEffect(() => {
    adminApi.products().then((items) => setProducts(items || [])).catch(() => setError('Could not load product filters.'))
  }, [])
  useEffect(() => {
    const timer = setTimeout(load, filters.q ? 250 : 0)
    return () => clearTimeout(timer)
  }, [pageIndex, filters])
  const setFilter = (key, value) => {
    setSelected(new Set())
    setFeedback('')
    setPageIndex(0)
    setFilters((current) => ({ ...current, [key]: value }))
  }
  const refresh = () => { setSelected(new Set()); load() }
  const changeStatus = async (uid, status) => {
    if (!window.confirm(`${status === 'REVOKED' ? 'Revoke' : 'Activate'} tag ${uid}?`)) return
    setProcessing(true)
    try {
      if (status === 'REVOKED') await adminApi.revokeTag(uid)
      else await adminApi.activateTag(uid)
      setSelected(new Set())
      setFeedback(`Tag ${uid} ${status === 'REVOKED' ? 'revoked' : 'activated'}.`)
      await load()
    } catch {
      setError('The tag status could not be updated.')
    } finally {
      setProcessing(false)
    }
  }
  const removeTag = async (uid) => {
    if (!window.confirm(`Permanently delete tag ${uid}? Its scan history will remain, but its HSM key will be deleted.`)) return
    setProcessing(true)
    try {
      await adminApi.deleteTag(uid)
      setSelected(new Set())
      setFeedback(`Tag ${uid} deleted.`)
      await load()
    } catch {
      setError('The tag could not be deleted. It may still be referenced or the HSM may be unavailable.')
    } finally {
      setProcessing(false)
    }
  }
  const runBulkAction = async (action) => {
    const uids = [...selected]
    if (!uids.length || processing) return
    if (action === 'DELETE' && !window.confirm(`Permanently delete ${uids.length} selected tag${uids.length === 1 ? '' : 's'} and their HSM keys? This cannot be undone.`)) return
    setProcessing(true)
    setError('')
    setFeedback('')
    try {
      const result = await adminApi.bulkTags(uids, action)
      setSelected(new Set())
      const label = action === 'REVOKE' ? 'revoked' : action === 'ACTIVATE' ? 'activated' : 'deleted'
      setFeedback(`${result.affected ?? uids.length} selected tag${(result.affected ?? uids.length) === 1 ? '' : 's'} ${label}.`)
      await load()
    } catch {
      setError(`The selected tags could not be ${action === 'DELETE' ? 'deleted' : action === 'REVOKE' ? 'revoked' : 'activated'}.`)
    } finally {
      setProcessing(false)
    }
  }
  const togglePageSelection = (checked) => {
    setSelected((current) => {
      const next = new Set(current)
      tags.forEach((tag) => checked ? next.add(tag.uid) : next.delete(tag.uid))
      return next
    })
  }
  const saveMetadata = async (event) => {
    event.preventDefault()
    try {
      const updated = await adminApi.updateTagMetadata(editing.uid, { displayName: editing.displayName, description: editing.description, imageUrl: editing.imageUrl })
      setTags((items) => items.map((tag) => tag.uid === updated.uid ? updated : tag))
      setEditing(null)
      setFeedback('Tag details saved.')
    } catch {
      setError('The tag metadata could not be saved.')
    }
  }
  const selectedAllOnPage = tags.length > 0 && tags.every((tag) => selected.has(tag.uid))
  const totalPages = Math.max(1, pageInfo.totalPages)
  return <div className="stack">
    <div className="page-intro"><p className="muted">Manage registered NFC identities and their current state. UID and AES keys are never editable here.</p><span className="refresh-label">{number(pageInfo.totalElements)} tags</span></div>
    {error && <div className="error-banner" role="alert">{error}</div>}
    {feedback && <div className="success-banner" role="status">{feedback}</div>}
    <section className="panel">
      <div className="tag-filters">
        <label className="search-box"><span aria-hidden="true">⌕</span><input aria-label="Search tags" value={filters.q} onChange={(event) => setFilter('q', event.target.value)} placeholder="Search UID, product, manufacturer, or details" /></label>
        <label className="filter-field"><span>Product</span><select value={filters.productId} onChange={(event) => setFilter('productId', event.target.value)}><option value="">All products</option>{products.map((product) => <option key={product.id} value={product.id}>{product.name}</option>)}</select></label>
        <label className="filter-field"><span>Manufacturer</span><select value={filters.manufacturer} onChange={(event) => setFilter('manufacturer', event.target.value)}><option value="">All manufacturers</option>{[...new Set(products.map((product) => product.manufacturer).filter(Boolean))].sort().map((manufacturer) => <option key={manufacturer} value={manufacturer}>{manufacturer}</option>)}</select></label>
        <label className="filter-field"><span>Status</span><select value={filters.status} onChange={(event) => setFilter('status', event.target.value)}><option value="">All statuses</option><option value="ACTIVE">Active</option><option value="REVOKED">Revoked</option></select></label>
        <button className="outline-button" onClick={refresh} disabled={loading || processing}>↻ Refresh</button>
      </div>
      {!readOnly && <div className="bulk-toolbar">
        <span className="muted">{selected.size} selected</span>
        <div className="bulk-actions">
          <button className="outline-button" disabled={!selected.size || processing} onClick={() => runBulkAction('ACTIVATE')}>Activate</button>
          <button className="danger-button" disabled={!selected.size || processing} onClick={() => runBulkAction('REVOKE')}>Revoke</button>
          <button className="danger-button" disabled={!selected.size || processing} onClick={() => runBulkAction('DELETE')}>Delete</button>
        </div>
      </div>}
      {loading ? <div className="empty-state">Loading tag registry…</div> : <div className="table-wrap"><table className="tag-table"><thead><tr>{!readOnly && <th><input type="checkbox" aria-label="Select all tags on this page" checked={selectedAllOnPage} onChange={(event) => togglePageSelection(event.target.checked)} /></th>}<th>TAG UID</th><th>PRODUCT</th><th>MANUFACTURER</th><th>LAST COUNTER</th><th>STATUS</th>{!readOnly && <th />}</tr></thead><tbody>{tags.map((tag) => <tr key={tag.uid}>
        {!readOnly && <td><input type="checkbox" aria-label={`Select tag ${tag.uid}`} checked={selected.has(tag.uid)} onChange={(event) => setSelected((current) => { const next = new Set(current); event.target.checked ? next.add(tag.uid) : next.delete(tag.uid); return next })} /></td>}
        <td data-label="TAG UID"><code>{tag.uid}</code></td><td data-label="PRODUCT"><strong>{tag.displayName || tag.productName || products.find((product) => product.id === tag.productId)?.name || 'Unassigned'}</strong>{tag.description && <small className="table-subtitle">{tag.description}</small>}</td>
        <td data-label="MANUFACTURER">{tag.manufacturer || products.find((product) => product.id === tag.productId)?.manufacturer || '—'}</td><td data-label="LAST COUNTER">{number(tag.lastScanCounter)}</td><td data-label="STATUS"><span className={`badge status-${String(tag.status || '').toLowerCase()}`}>{tag.status || 'UNKNOWN'}</span></td>
        {!readOnly && <td className="actions"><button className="outline-button" disabled={processing} onClick={() => setEditing({ ...tag })}>Edit</button>{String(tag.status).toLowerCase() !== 'revoked' ? <button className="danger-button" disabled={processing} onClick={() => changeStatus(tag.uid, 'REVOKED')}>Revoke</button> : <button className="outline-button" disabled={processing} onClick={() => changeStatus(tag.uid, 'ACTIVE')}>Activate</button>}<button className="danger-button" disabled={processing} onClick={() => removeTag(tag.uid)}>Delete</button></td>}
      </tr>)}</tbody></table>{tags.length === 0 && <div className="empty-state">No tags match these filters.</div>}</div>}
      <div className="pagination"><span className="muted">Page {pageIndex + 1} of {totalPages}</span><div><button className="outline-button" disabled={pageIndex === 0 || loading} onClick={() => { setSelected(new Set()); setPageIndex((current) => Math.max(0, current - 1)) }}>Previous</button><button className="outline-button" disabled={pageIndex + 1 >= totalPages || loading} onClick={() => { setSelected(new Set()); setPageIndex((current) => current + 1) }}>Next</button></div></div>
    </section>
    {editing && <div className="modal-backdrop"><form className="modal panel" onSubmit={saveMetadata}><div className="panel-heading"><div><h2>Edit product label</h2><span className="muted">{editing.uid}</span></div><button type="button" className="text-button" onClick={() => setEditing(null)}>Close</button></div><label>Display name<input value={editing.displayName || ''} onChange={(event) => setEditing({ ...editing, displayName: event.target.value })} placeholder="e.g. Blue bottle — batch A" /></label><label>Description<textarea value={editing.description || ''} onChange={(event) => setEditing({ ...editing, description: event.target.value })} placeholder="Short product description" /></label><label>Image URL<input type="url" value={editing.imageUrl || ''} onChange={(event) => setEditing({ ...editing, imageUrl: event.target.value })} placeholder="https://..." /></label>{editing.imageUrl && <img className="metadata-preview" src={editing.imageUrl} alt="Product preview" /> }<div className="modal-actions"><button type="button" className="outline-button" onClick={() => setEditing(null)}>Cancel</button><button className="primary-button" type="submit">Save details</button></div></form></div>}
  </div>
}

function SecurityPage() {
  const [events, setEvents] = useState([])
  const [verifiedScans, setVerifiedScans] = useState([])
  const [error, setError] = useState('')
  const [refreshing, setRefreshing] = useState(false)
  const load = async () => {
    try {
      const [securityEvents, scans] = await Promise.all([adminApi.securityEvents(), adminApi.scans()])
      setEvents(securityEvents || [])
      setVerifiedScans((scans || []).filter((scan) => String(scan.result).toUpperCase() === 'REAL'))
      setError('')
    } catch { setError('Could not load security events.') }
  }
  const refreshNow = async () => {
    setRefreshing(true)
    try { await load() } finally { setRefreshing(false) }
  }
  useEffect(() => { load(); const timer = setInterval(load, 15000); return () => clearInterval(timer) }, [])
  return <div className="stack"><div className="page-intro"><p className="muted">Monitor suspicious scans and verification anomalies.</p><div className="page-actions"><span className="refresh-label">Auto-refreshes every 15s</span><button className="outline-button" onClick={refreshNow} disabled={refreshing}>{refreshing ? 'Refreshing…' : '↻ Refresh now'}</button></div></div>{error && <div className="error-banner">{error}</div>}<div className="security-summary"><div className="alert-card"><span className="alert-icon">!</span><div><strong>{events.length} recorded events</strong><span>Each stored scan-log event</span></div></div><div className="panel security-note"><span className="shield-icon">◈</span><div><strong>Verification integrity</strong><span>Events are read directly from the PostgreSQL-backed API.</span></div></div></div><section className="panel"><div className="panel-heading"><div><h2>Security event log</h2><span className="muted">Replay attempts, invalid signatures, and unknown tags</span></div></div><ScanTable rows={events} security /></section><section className="panel"><div className="panel-heading"><div><h2>Verified scan location evidence</h2><span className="muted">GeoIP is shown only when neither device GPS nor client coordinates were supplied.</span></div></div><ScanTable rows={verifiedScans} security /></section></div>
}

function ProvisioningPage({ isAdmin }) {
  const [products, setProducts] = useState([])
  const [file, setFile] = useState(null)
  const [result, setResult] = useState(null)
  const [product, setProduct] = useState({ name: '', manufacturer: '' })
  const [error, setError] = useState('')
  const [exporting, setExporting] = useState(false)
  const loadProducts = async () => { try { setProducts(await adminApi.products()) } catch { setError('Could not load products.') } }
  useEffect(() => { loadProducts() }, [])
  const create = async (event) => { event.preventDefault(); try { await adminApi.createProduct(product); setProduct({ name: '', manufacturer: '' }); await loadProducts() } catch { setError('Could not create the product.') } }
  const importFile = async () => { if (!file) return; try { setResult(await adminApi.importProvisioning(file)); setError(''); await loadProducts() } catch { setError('The provisioning CSV could not be imported.') } }
  const exportBatch = async () => {
    if (!result?.batchId || !isAdmin) return
    const confirmed = window.confirm(`This will download AES keys for ${result.importedRows} tag(s) in this provisioning batch. The CSV is highly sensitive; store it securely and do not share it publicly. Continue?`)
    if (!confirmed) return
    setExporting(true)
    try {
      const blob = await adminApi.exportProvisioningKeys(result.batchId)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `provisioning-${result.batchId}-keys.csv`
      link.click()
      URL.revokeObjectURL(url)
      setError('')
    } catch {
      setError('The key CSV could not be exported. Check HSM availability and administrator permissions.')
    } finally {
      setExporting(false)
    }
  }
  const removeProduct = async (item) => {
    const hasKnownTagCount = Number.isInteger(item.tagCount)
    const linkedTags = hasKnownTagCount ? `${item.tagCount} attached tag${item.tagCount === 1 ? '' : 's'}` : 'all linked tags'
    const confirmation = `PERMANENTLY DELETE PRODUCT "${item.name}"?\n\nThis will delete ${linkedTags}, all HSM keys linked to those tags, and the product itself. This action cannot be undone.\n\nContinue only if you intend to permanently remove this product and its linked identity data.`
    if (!window.confirm(confirmation)) return
    try { await adminApi.deleteProduct(item.id); await loadProducts(); setError('') } catch { setError('The product could not be deleted.') }
  }
  return <div className="stack"><div className="page-intro"><div><p className="muted">Import virtual NTAG records created by the desktop simulator. Key exports are available only to administrators and are highly sensitive.</p></div></div>{error && <div className="error-banner">{error}</div>}<div className="content-grid"><section className="panel"><div className="panel-heading"><div><h2>Create product</h2><span className="muted">Name and manufacturer are required.</span></div></div><form onSubmit={create} className="stack"><label>Product name<input value={product.name} onChange={(event) => setProduct({ ...product, name: event.target.value })} required /></label><label>Manufacturer<input value={product.manufacturer} onChange={(event) => setProduct({ ...product, manufacturer: event.target.value })} required /></label><button className="primary-button" type="submit">Create product</button></form></section><section className="panel"><div className="panel-heading"><div><h2>Import provisioning CSV</h2><span className="muted">Required columns: uid, aesKey, productId</span></div></div><input type="file" accept=".csv,text/csv" onChange={(event) => setFile(event.target.files?.[0] || null)} /><p className="muted">{file ? file.name : 'Choose the CSV exported by the desktop virtual-tag simulator.'}</p><button className="primary-button" disabled={!file} onClick={importFile}>Validate and import</button>{result && <div className="import-result"><strong>{result.importedRows} tag{result.importedRows === 1 ? '' : 's'} provisioned · {result.status}</strong><span>{result.totalRows} CSV rows · {result.duplicateRows} duplicates · {result.invalidRows} invalid</span>{isAdmin && result.importedRows > 0 && <button className="outline-button" disabled={exporting} onClick={exportBatch}>{exporting ? 'Preparing secure CSV…' : 'Export AES-key CSV'}</button>}</div>}</section></div><section className="panel"><div className="panel-heading"><div><h2>Products</h2><span className="muted">Each product includes its attached tag count. Select a product ID when creating virtual tags.</span></div></div>{products.length ? <div className="table-wrap"><table><thead><tr><th>NAME</th><th>MANUFACTURER</th><th>PRODUCT ID</th><th>ATTACHED TAGS</th><th /></tr></thead><tbody>{products.map((item) => <tr key={item.id}><td>{item.name}</td><td>{item.manufacturer}</td><td><code>{item.id}</code></td><td>{Number.isInteger(item.tagCount) ? number(item.tagCount) : 'Unknown'}</td><td><button className="danger-button" onClick={() => removeProduct(item)}>Delete</button></td></tr>)}</tbody></table></div> : <div className="empty-state">No products yet.</div>}</section></div>
}

createRoot(document.getElementById('root')).render(<App />)
