import 'leaflet/dist/leaflet.css'
import type { LatLngBoundsExpression } from 'leaflet'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { CircleMarker, MapContainer, Popup, TileLayer } from 'react-leaflet'
import { LanguageToggle } from '@/components/LanguageToggle'
import { ReunificationSearch } from '@/family/ReunificationSearch'
import { fetchPublicCamps, type LocatorCamp } from '@/world/api'
import { BANGLADESH_DELTA_CENTER, TILES } from '@/world/mapTheme'

const OPEN_COLOR = '#16a34a'
const CLOSED_COLOR = '#94a3b8'

/**
 * The public, no-login camp finder. A displaced person can open this with no account and see
 * every shelter by name, location, and whether it is open — and nothing else. Calm, large-type,
 * mobile-first: the field design, for someone acting under stress.
 */
export default function Locator() {
  const { t, i18n } = useTranslation()
  const [camps, setCamps] = useState<LocatorCamp[] | null>(null)
  const [failed, setFailed] = useState(false)
  const [query, setQuery] = useState('')

  useEffect(() => {
    let active = true
    fetchPublicCamps()
      .then((data) => active && setCamps(data))
      .catch(() => active && setFailed(true))
    return () => {
      active = false
    }
  }, [])

  const name = (camp: LocatorCamp) => (i18n.language === 'bn' ? camp.nameBn : camp.nameEn)
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q || !camps) return camps ?? []
    return camps.filter((c) => c.nameEn.toLowerCase().includes(q) || c.nameBn.includes(query.trim()))
  }, [camps, query])

  const bounds: LatLngBoundsExpression | undefined =
    camps && camps.length > 0 ? camps.map((c) => [c.lat, c.lng] as [number, number]) : undefined

  return (
    <div data-theme="field" className="flex min-h-svh flex-col bg-bg text-ink">
      <header className="flex items-center gap-3 border-b border-line bg-surface px-5 py-4">
        <span className="font-mono text-xs tracking-[0.16em] text-ink-muted uppercase">
          {t('appNameShort')}
        </span>
        <div className="ml-auto flex items-center gap-2">
          <LanguageToggle />
          <Link
            to="/login"
            className="inline-flex h-9 items-center rounded-full border border-line px-3.5 text-xs text-ink-muted transition-colors hover:border-line-strong hover:text-ink"
          >
            {t('locator.signIn')}
          </Link>
        </div>
      </header>

      <div className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-5 px-5 py-8 lg:flex-row">
        <section className="lg:w-96 lg:shrink-0">
          <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">{t('locator.title')}</h1>
          <p className="mt-2 text-ink-muted">{t('locator.subtitle')}</p>

          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('locator.search')}
            aria-label={t('locator.search')}
            className="mt-4 h-12 w-full rounded-xl border border-line bg-surface px-4 text-base outline-none focus-visible:border-signal focus-visible:ring-2 focus-visible:ring-signal/40"
          />

          <div className="mt-4 space-y-2" aria-live="polite">
            {failed && <p className="text-sm text-crit">{t('locator.error')}</p>}
            {camps && filtered.length === 0 && !failed && (
              <p className="text-sm text-ink-muted">{t('locator.empty')}</p>
            )}
            {filtered.map((camp) => (
              <div
                key={camp.id}
                className="flex items-center justify-between gap-3 rounded-xl border border-line bg-surface px-4 py-3"
              >
                <span className="font-medium">{name(camp)}</span>
                <StatusPill status={camp.status} />
              </div>
            ))}
          </div>

          <ReunificationSearch />
        </section>

        <section className="min-h-80 flex-1 overflow-hidden rounded-2xl border border-line">
          <MapContainer
            center={BANGLADESH_DELTA_CENTER}
            zoom={7}
            bounds={bounds}
            boundsOptions={{ padding: [30, 30] }}
            className="h-full min-h-80 w-full"
            aria-label={t('locator.mapLabel')}
          >
            <TileLayer url={TILES.light.url} attribution={TILES.light.attribution} />
            {(camps ?? []).map((camp) => (
              <CircleMarker
                key={camp.id}
                center={[camp.lat, camp.lng]}
                radius={9}
                pathOptions={markerStyle(camp.status)}
              >
                <Popup>
                  <div className="font-sans">
                    <p className="text-sm font-semibold text-neutral-900">{name(camp)}</p>
                    <p
                      className="mt-1 text-xs font-medium"
                      style={{ color: camp.status === 'OPEN' ? OPEN_COLOR : CLOSED_COLOR }}
                    >
                      {t(camp.status === 'OPEN' ? 'map.status.open' : 'map.status.closed')}
                    </p>
                  </div>
                </Popup>
              </CircleMarker>
            ))}
          </MapContainer>
        </section>
      </div>
    </div>
  )
}

function StatusPill({ status }: { status: LocatorCamp['status'] }) {
  const { t } = useTranslation()
  const open = status === 'OPEN'
  return (
    <span
      className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium"
      style={{
        color: open ? OPEN_COLOR : CLOSED_COLOR,
        backgroundColor: open ? 'rgba(22,163,74,0.1)' : 'rgba(148,163,184,0.15)',
      }}
    >
      <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: open ? OPEN_COLOR : CLOSED_COLOR }} />
      {t(open ? 'map.status.open' : 'map.status.closed')}
    </span>
  )
}

function markerStyle(status: LocatorCamp['status']) {
  const color = status === 'OPEN' ? OPEN_COLOR : CLOSED_COLOR
  return { color, weight: 1.5, fillColor: color, fillOpacity: status === 'OPEN' ? 0.6 : 0.3 }
}
