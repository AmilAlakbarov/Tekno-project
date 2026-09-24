import React, { useEffect, useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { adminApi } from './api'
import './styles.css'

const navItems = [
  { id: 'home', label: 'Overview', icon: '⌂' },
  { id: 'tags', label: 'Tag registry', icon: '⌁' },
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
          {page === 'tags' ? <TagsPage /> : page === 'security' ? <SecurityPage /> : <HomePage navigate={navigate} />}
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
      setOverview(summary); setScans(recent || []); setLocations(points || []); setError('')
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
      <section className="panel"><div className="panel-heading"><div><h2>Network locations</h2><span className="muted">{number(locations.length)} active coordinates</span></div></div><div className="map-placeholder"><div className="map-grid" /><span className="map-pin pin-one" /><span className="map-pin pin-two" /><span className="map-pin pin-three" /><div className="map-caption">Live scan distribution</div></div></section>
    </div>
    <section className="panel"><div className="panel-heading"><div><h2>Latest scans</h2><span className="muted">The most recent activity across your tags</span></div><button className="text-button" onClick={() => navigate('security')}>All activity →</button></div>
      <ScanTable rows={scans.slice(0, 6)} />
    </section>
  </div>
}

function ScanTable({ rows, security = false }) {
  if (!rows.length) return <div className="empty-state">No scan activity recorded yet.</div>
  return <div className="table-wrap"><table><thead><tr><th>TAG UID</th><th>TIME</th><th>RESULT</th>{security && <th>LOCATION</th>}</tr></thead><tbody>{rows.map((row, index) => <tr key={`${row.uid}-${row.timestamp}-${index}`}><td><code>{row.uid || '—'}</code></td><td>{timeAgo(row.timestamp)}</td><td><span className={`badge ${String(row.result || '').toLowerCase()}`}>{row.result || 'UNKNOWN'}</span></td>{security && <td>{row.latitude != null ? `${Number(row.latitude).toFixed(3)}, ${Number(row.longitude).toFixed(3)}` : 'No location'}</td>}</tr>)}</tbody></table></div>
}

function TagsPage() {
  const [tags, setTags] = useState([])
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const load = async () => { setLoading(true); try { const result = await adminApi.tags({ page: 0, size: 100, uid: query }); setTags(result.content || []); setError('') } catch { setError('Could not load the tag registry.') } finally { setLoading(false) } }
  useEffect(() => { load() }, [])
  useEffect(() => { const timer = setTimeout(load, 300); return () => clearTimeout(timer) }, [query])
  const revoke = async (uid) => { if (!window.confirm(`Revoke tag ${uid}?`)) return; try { const updated = await adminApi.revokeTag(uid); setTags((items) => items.map((tag) => tag.uid === uid ? updated : tag)) } catch { setError('The tag could not be revoked.') } }
  return <div className="stack"><div className="page-intro"><p className="muted">Manage registered NFC identities and their current state.</p><span className="refresh-label">{tags.length} tags shown</span></div>{error && <div className="error-banner">{error}</div>}<section className="panel"><div className="toolbar"><div className="search-box"><span>⌕</span><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search by tag UID..." /></div><button className="outline-button" onClick={load}>↻ Refresh</button></div>{loading ? <div className="empty-state">Loading tag registry…</div> : <div className="table-wrap"><table><thead><tr><th>TAG UID</th><th>PRODUCT</th><th>LAST COUNTER</th><th>STATUS</th><th /></tr></thead><tbody>{tags.map((tag) => <tr key={tag.uid}><td><code>{tag.uid}</code></td><td>{tag.productName || 'Unassigned'}</td><td>{number(tag.lastScanCounter)}</td><td><span className={`badge status-${String(tag.status || '').toLowerCase()}`}>{tag.status || 'UNKNOWN'}</span></td><td className="actions">{String(tag.status).toLowerCase() !== 'revoked' && <button className="danger-button" onClick={() => revoke(tag.uid)}>Revoke</button>}</td></tr>)}</tbody></table></div>}</section></div>
}

function SecurityPage() {
  const [events, setEvents] = useState([])
  const [error, setError] = useState('')
  const load = async () => { try { setEvents(await adminApi.securityEvents() || []); setError('') } catch { setError('Could not load security events.') } }
  useEffect(() => { load(); const timer = setInterval(load, 15000); return () => clearInterval(timer) }, [])
  const flagged = useMemo(() => events.filter((event) => !['REAL', 'VALID', 'SUCCESS'].includes(String(event.result).toUpperCase())), [events])
  return <div className="stack"><div className="page-intro"><p className="muted">Monitor suspicious scans and verification anomalies.</p><span className="refresh-label">Auto-refreshes every 15s</span></div>{error && <div className="error-banner">{error}</div>}<div className="security-summary"><div className="alert-card"><span className="alert-icon">!</span><div><strong>{flagged.length} flagged events</strong><span>Requires review</span></div></div><div className="panel security-note"><span className="shield-icon">◈</span><div><strong>Verification integrity</strong><span>Events are read directly from the PostgreSQL-backed API.</span></div></div></div><section className="panel"><div className="panel-heading"><div><h2>Security event log</h2><span className="muted">Replay attempts, invalid signatures, and unknown tags</span></div></div><ScanTable rows={events} security /></section></div>
}

createRoot(document.getElementById('root')).render(<App />)
