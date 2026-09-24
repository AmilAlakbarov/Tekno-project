import React, { useEffect, useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { adminApi } from './api'
import './styles.css'

const navItems = [
  { id: 'home', label: 'Overview', icon: '⌂' },
  { id: 'tags', label: 'Tag registry', icon: '⌁' },
  { id: 'provisioning', label: 'Provisioning', icon: '+' },
  { id: 'security', label: 'Security events', icon: '◈' }
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
  const [page, setPage] = useState(window.location.hash.slice(1) || 'home')
  const [mobileNav, setMobileNav] = useState(false)

  useEffect(() => {
    const onHash = () => setPage(window.location.hash.slice(1) || 'home')
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  }, [])

  const navigate = (next) => {
    window.location.hash = next
    setMobileNav(false)
  }

  return (
    <div className="app-shell">
      <aside className={`sidebar ${mobileNav ? 'sidebar-open' : ''}`}>
        <div className="brand"><span className="brand-mark">A</span><span>AUTHENTICHAIN</span></div>
        <div className="workspace-label">CONTROL CENTER</div>
        <nav>
          {navItems.map((item) => (
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
          <div className="topbar-meta"><span className="live-pill"><span className="status-dot" /> LIVE</span><span className="avatar">AC</span></div>
        </header>
        <div className="page-content">
          {page === 'tags' ? <TagsPage /> : page === 'provisioning' ? <ProvisioningPage /> : page === 'security' ? <SecurityPage /> : <HomePage navigate={navigate} />}
        </div>
      </main>
    </div>
  )
}

function StatCard({ label, value, detail, accent }) {
  return <div className="stat-card"><div className={`stat-accent ${accent || ''}`} /><div className="stat-label">{label}</div><div className="stat-value">{value}</div><div className="stat-detail">{detail}</div></div>
}

function HomePage({ navigate }) {
  const [overview, setOverview] = useState(null)
  const [scans, setScans] = useState([])
  const [locations, setLocations] = useState([])
  const [error, setError] = useState('')

  const load = async () => {
    try {
      const [summary, recent, points] = await Promise.all([adminApi.overview(), adminApi.scans(), adminApi.locations()])
      setOverview(summary); setScans(uniqueScans(recent || [])); setLocations(points || []); setError('')
    } catch { setError('Unable to reach the API. Check VITE_API_URL and that the backend is running.') }
  }
  useEffect(() => { load(); const timer = setInterval(load, 15000); return () => clearInterval(timer) }, [])
  const maxScans = Math.max(...scans.slice(0, 7).map((scan) => scan.timestamp ? 1 : 0), 1)
  const successRate = overview?.successRate ?? 0

  return <div className="stack">
    <div className="page-intro"><div><p className="muted">A clear view of your physical product identity network.</p></div><span className="refresh-label">Auto-refreshes every 15s</span></div>
    {error && <div className="error-banner">{error}</div>}
    <div className="stats-grid">
      <StatCard label="ACTIVE TAGS" value={number(overview?.activeTags)} detail={`${number(overview?.totalTags)} registered total`} accent="purple" />
      <StatCard label="SCANS · LAST 24H" value={number(overview?.scansLast24Hours)} detail={`${number(overview?.successfulScansLast24Hours)} verified successfully`} accent="blue" />
      <StatCard label="SUCCESS RATE" value={`${Number(successRate).toFixed(1)}%`} detail="of scans verified as real" accent="green" />
    </div>
    <div className="content-grid">
      <section className="panel chart-panel"><div className="panel-heading"><div><h2>Scan activity</h2><span className="muted">Recent verification attempts</span></div><button className="text-button" onClick={() => navigate('security')}>View security →</button></div>
        <div className="activity-chart">{[70, 40, 82, 55, 90, 52, 68].map((height, index) => <div className="bar-group" key={index}><div className="bar" style={{ height: `${height}%` }} /><span>{['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'][index]}</span></div>)}</div>
        <div className="chart-legend"><span><i className="legend-dot verified" /> Verified</span><span><i className="legend-dot flagged" /> Flagged</span></div>
      </section>
      <section className="panel"><div className="panel-heading"><div><h2>Network locations</h2><span className="muted">{number(locations.length)} coordinates received</span></div></div><div className="map-placeholder"><div className="map-grid" />{locations.slice(0, 12).map((point, index) => <span className="map-pin" key={`${point.latitude}-${point.longitude}-${index}`} style={{ left: `${12 + ((Math.abs(point.longitude) * 7) % 76)}%`, top: `${15 + ((Math.abs(point.latitude) * 3) % 68)}%` }} />)}<div className="map-caption">{locations.length ? 'Recent API scan coordinates' : 'NFC scans do not include GPS coordinates'}</div></div></section>
    </div>
    <section className="panel"><div className="panel-heading"><div><h2>Latest scans</h2><span className="muted">The most recent activity across your tags</span></div><button className="text-button" onClick={() => navigate('security')}>All activity →</button></div>
      <ScanTable rows={scans.slice(0, 6)} />
    </section>
  </div>
}

function ScanTable({ rows, security = false }) {
  if (!rows.length) return <div className="empty-state">No scan activity recorded yet.</div>
  const visibleRows = uniqueScans(rows)
  return <div className="table-wrap"><table><thead><tr><th>TAG UID</th><th>TIME</th><th>RESULT</th><th>RECEIVED CTR</th><th>EXPECTED CTR</th>{security && <th>LOCATION</th>}</tr></thead><tbody>{visibleRows.map((row, index) => <tr key={row.id || `${row.uid}-${row.timestamp}-${index}`}><td><code>{row.uid || '—'}</code></td><td>{timeAgo(row.timestamp)}</td><td><span className={`badge ${String(row.result || '').toLowerCase()}`}>{row.result || 'UNKNOWN'}</span></td><td>{row.receivedCounter ?? '—'}</td><td>{row.expectedCounter ?? '—'}</td>{security && <td>{row.latitude != null ? `${Number(row.latitude).toFixed(3)}, ${Number(row.longitude).toFixed(3)}` : 'NFC location unavailable'}</td>}</tr>)}</tbody></table></div>
}

function TagsPage() {
  const [tags, setTags] = useState([])
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState(null)
  const load = async () => { setLoading(true); try { const result = await adminApi.tags({ page: 0, size: 100, uid: query }); setTags(result.content || []); setError('') } catch { setError('Could not load the tag registry.') } finally { setLoading(false) } }
  useEffect(() => { load() }, [])
  useEffect(() => { const timer = setTimeout(load, 300); return () => clearTimeout(timer) }, [query])
  const changeStatus = async (uid, status) => { if (!window.confirm(`${status === 'REVOKED' ? 'Revoke' : 'Activate'} tag ${uid}?`)) return; try { const updated = status === 'REVOKED' ? await adminApi.revokeTag(uid) : await adminApi.activateTag(uid); setTags((items) => items.map((tag) => tag.uid === uid ? updated : tag)) } catch { setError('The tag status could not be updated.') } }
  const saveMetadata = async (event) => { event.preventDefault(); try { const updated = await adminApi.updateTagMetadata(editing.uid, { displayName: editing.displayName, description: editing.description, imageUrl: editing.imageUrl }); setTags((items) => items.map((tag) => tag.uid === updated.uid ? updated : tag)); setEditing(null) } catch { setError('The tag metadata could not be saved.') } }
  return <div className="stack"><div className="page-intro"><p className="muted">Manage registered NFC identities and their current state. UID and AES keys are never editable here.</p><span className="refresh-label">{tags.length} tags shown</span></div>{error && <div className="error-banner">{error}</div>}<section className="panel"><div className="toolbar"><div className="search-box"><span>⌕</span><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search by tag UID..." /></div><button className="outline-button" onClick={load}>↻ Refresh</button></div>{loading ? <div className="empty-state">Loading tag registry…</div> : <div className="table-wrap"><table><thead><tr><th>TAG UID</th><th>PRODUCT</th><th>LAST COUNTER</th><th>STATUS</th><th /></tr></thead><tbody>{tags.map((tag) => <tr key={tag.uid}><td><code>{tag.uid}</code></td><td><strong>{tag.displayName || tag.productName || 'Unassigned'}</strong>{tag.description && <small className="table-subtitle">{tag.description}</small>}</td><td>{number(tag.lastScanCounter)}</td><td><span className={`badge status-${String(tag.status || '').toLowerCase()}`}>{tag.status || 'UNKNOWN'}</span></td><td className="actions"><button className="outline-button" onClick={() => setEditing({ ...tag })}>Edit</button>{String(tag.status).toLowerCase() !== 'revoked' ? <button className="danger-button" onClick={() => changeStatus(tag.uid, 'REVOKED')}>Revoke</button> : <button className="outline-button" onClick={() => changeStatus(tag.uid, 'ACTIVE')}>Activate</button>}</td></tr>)}</tbody></table></div>}</section>{editing && <div className="modal-backdrop"><form className="modal panel" onSubmit={saveMetadata}><div className="panel-heading"><div><h2>Edit product label</h2><span className="muted">{editing.uid}</span></div><button type="button" className="text-button" onClick={() => setEditing(null)}>Close</button></div><label>Display name<input value={editing.displayName || ''} onChange={(event) => setEditing({ ...editing, displayName: event.target.value })} placeholder="e.g. Blue bottle — batch A" /></label><label>Description<textarea value={editing.description || ''} onChange={(event) => setEditing({ ...editing, description: event.target.value })} placeholder="Short product description" /></label><label>Image URL<input type="url" value={editing.imageUrl || ''} onChange={(event) => setEditing({ ...editing, imageUrl: event.target.value })} placeholder="https://..." /></label>{editing.imageUrl && <img className="metadata-preview" src={editing.imageUrl} alt="Product preview" /> }<div className="modal-actions"><button type="button" className="outline-button" onClick={() => setEditing(null)}>Cancel</button><button className="primary-button" type="submit">Save details</button></div></form></div>}</div>
}

function SecurityPage() {
  const [events, setEvents] = useState([])
  const [error, setError] = useState('')
  const load = async () => { try { setEvents(await adminApi.securityEvents() || []); setError('') } catch { setError('Could not load security events.') } }
  useEffect(() => { load(); const timer = setInterval(load, 15000); return () => clearInterval(timer) }, [])
  const flagged = useMemo(() => events.filter((event) => !['REAL', 'VALID', 'SUCCESS'].includes(String(event.result).toUpperCase())), [events])
  return <div className="stack"><div className="page-intro"><p className="muted">Monitor suspicious scans and verification anomalies.</p><span className="refresh-label">Auto-refreshes every 15s</span></div>{error && <div className="error-banner">{error}</div>}<div className="security-summary"><div className="alert-card"><span className="alert-icon">!</span><div><strong>{uniqueScans(events).length} flagged events</strong><span>Unique UID/counter events</span></div></div><div className="panel security-note"><span className="shield-icon">◈</span><div><strong>Verification integrity</strong><span>Events are read directly from the PostgreSQL-backed API.</span></div></div></div><section className="panel"><div className="panel-heading"><div><h2>Security event log</h2><span className="muted">Replay attempts, invalid signatures, and unknown tags</span></div></div><ScanTable rows={uniqueScans(events)} security /></section></div>
}

function ProvisioningPage() {
  const [products, setProducts] = useState([])
  const [file, setFile] = useState(null)
  const [result, setResult] = useState(null)
  const [product, setProduct] = useState({ name: '', manufacturer: '' })
  const [error, setError] = useState('')
  const loadProducts = async () => { try { setProducts(await adminApi.products()) } catch { setError('Could not load products.') } }
  useEffect(() => { loadProducts() }, [])
  const create = async (event) => { event.preventDefault(); try { await adminApi.createProduct(product); setProduct({ name: '', manufacturer: '' }); await loadProducts() } catch { setError('Could not create the product.') } }
  const importFile = async () => { if (!file) return; try { setResult(await adminApi.importProvisioning(file)); setError('') } catch { setError('The provisioning CSV could not be imported.') } }
  return <div className="stack"><div className="page-intro"><div><p className="muted">Import virtual NTAG records created by the desktop simulator. AES keys are never displayed.</p></div></div>{error && <div className="error-banner">{error}</div>}<div className="content-grid"><section className="panel"><div className="panel-heading"><div><h2>Create product</h2><span className="muted">Name and manufacturer are required.</span></div></div><form onSubmit={create} className="stack"><label>Product name<input value={product.name} onChange={(event) => setProduct({ ...product, name: event.target.value })} required /></label><label>Manufacturer<input value={product.manufacturer} onChange={(event) => setProduct({ ...product, manufacturer: event.target.value })} required /></label><button className="primary-button" type="submit">Create product</button></form></section><section className="panel"><div className="panel-heading"><div><h2>Import provisioning CSV</h2><span className="muted">Required columns: uid, aesKey, productId</span></div></div><input type="file" accept=".csv,text/csv" onChange={(event) => setFile(event.target.files?.[0] || null)} /><p className="muted">{file ? file.name : 'Choose the CSV exported by the desktop virtual-tag simulator.'}</p><button className="primary-button" disabled={!file} onClick={importFile}>Validate and import</button>{result && <div className="import-result"><strong>{result.status}</strong><span>{result.importedRows} imported · {result.duplicateRows} duplicates · {result.invalidRows} invalid</span></div>}</section></div><section className="panel"><div className="panel-heading"><div><h2>Products</h2><span className="muted">Select a product ID when creating virtual tags.</span></div></div>{products.length ? <div className="table-wrap"><table><thead><tr><th>NAME</th><th>MANUFACTURER</th><th>PRODUCT ID</th></tr></thead><tbody>{products.map((item) => <tr key={item.id}><td>{item.name}</td><td>{item.manufacturer}</td><td><code>{item.id}</code></td></tr>)}</tbody></table></div> : <div className="empty-state">No products yet.</div>}</section></div>
}

createRoot(document.getElementById('root')).render(<App />)
