import 'leaflet/dist/leaflet.css'
import { Fragment } from 'react'
import type { LatLngBoundsExpression } from 'leaflet'
import { useTranslation } from 'react-i18next'
import { CircleMarker, MapContainer, Polygon, Popup, TileLayer, Tooltip } from 'react-leaflet'
import type { Camp, Disaster } from '@/world/api'
import { BANGLADESH_DELTA_CENTER, DISASTER_COLOR, TILES, toLeafletRings } from '@/world/mapTheme'

/**
 * The operator situation-room map: both disaster worlds on one dark map of the Bengal delta,
 * each a distinct color (flood cyan, cyclone amber). Camp markers carry a camp's identity and
 * headline state; the affected areas are drawn as translucent polygons underneath.
 */
export function WorldMap({ disasters }: { disasters: Disaster[] }) {
  const { t, i18n } = useTranslation()
  const lang = i18n.language
  const name = (en: string, bn: string) => (lang === 'bn' ? bn : en)

  const allCamps = disasters.flatMap((d) => d.camps)
  const bounds = boundsOf(allCamps)

  return (
    <div className="relative h-full w-full overflow-hidden">
      <MapContainer
        center={BANGLADESH_DELTA_CENTER}
        zoom={7}
        bounds={bounds}
        boundsOptions={{ padding: [40, 40] }}
        zoomControl={false}
        className="h-full w-full bg-bg"
        aria-label={t('map.regionLabel')}
      >
        <TileLayer url={TILES.dark.url} attribution={TILES.dark.attribution} />

        {disasters.map((disaster) => {
          const color = DISASTER_COLOR[disaster.type]
          return (
            <Fragment key={disaster.id}>
              {disaster.affectedAreas.map((area) => (
                <Polygon
                  key={area.id}
                  positions={toLeafletRings(area.geometry)}
                  pathOptions={{ color, weight: 1.5, opacity: 0.7, fillOpacity: 0.08, dashArray: '4 5' }}
                />
              ))}
              {disaster.camps.map((camp) => (
                <CircleMarker
                  key={camp.id}
                  center={[camp.lat, camp.lng]}
                  radius={radiusFor(camp)}
                  pathOptions={markerStyle(camp, color)}
                >
                  <Tooltip direction="top" offset={[0, -4]}>
                    {name(camp.nameEn, camp.nameBn)}
                  </Tooltip>
                  <Popup>
                    <CampPopup camp={camp} disasterName={name(disaster.nameEn, disaster.nameBn)} accent={color} />
                  </Popup>
                </CircleMarker>
              ))}
            </Fragment>
          )
        })}
      </MapContainer>

      <Legend disasters={disasters} />
    </div>
  )
}

function CampPopup({ camp, disasterName, accent }: { camp: Camp; disasterName: string; accent: string }) {
  const { t, i18n } = useTranslation()
  const occupancy = camp.capacity > 0 ? Math.min(1, camp.population / camp.capacity) : 0
  const closed = camp.status === 'CLOSED'

  return (
    <div className="min-w-52 font-sans">
      <p className="text-[11px] font-medium tracking-wide uppercase" style={{ color: accent }}>
        {disasterName}
      </p>
      <p className="mt-0.5 text-sm font-semibold text-neutral-900">
        {i18n.language === 'bn' ? camp.nameBn : camp.nameEn}
      </p>
      <p className="mt-1 text-xs font-medium" style={{ color: closed ? '#9ca3af' : '#16a34a' }}>
        {t(closed ? 'map.status.closed' : 'map.status.open')}
      </p>

      {!closed && (
        <div className="mt-2">
          <div className="flex justify-between text-xs text-neutral-600">
            <span>{t('map.sheltered')}</span>
            <span className="font-mono text-neutral-900">
              {camp.population.toLocaleString()} / {camp.capacity.toLocaleString()}
            </span>
          </div>
          <div className="mt-1 h-1.5 w-full overflow-hidden rounded-full bg-neutral-200">
            <div
              className="h-full rounded-full"
              style={{ width: `${occupancy * 100}%`, backgroundColor: occupancy > 0.9 ? '#dc2626' : accent }}
            />
          </div>
        </div>
      )}
    </div>
  )
}

function Legend({ disasters }: { disasters: Disaster[] }) {
  const { t, i18n } = useTranslation()
  return (
    <div className="pointer-events-none absolute bottom-3 left-3 z-[1000] rounded-lg border border-line bg-surface/90 px-3 py-2.5 backdrop-blur">
      <p className="font-mono text-[10px] tracking-[0.16em] text-ink-muted uppercase">{t('map.legend')}</p>
      <ul className="mt-1.5 space-y-1">
        {disasters.map((disaster) => (
          <li key={disaster.id} className="flex items-center gap-2 text-xs text-ink">
            <span
              className="h-2.5 w-2.5 shrink-0 rounded-full"
              style={{ backgroundColor: DISASTER_COLOR[disaster.type] }}
              aria-hidden
            />
            {i18n.language === 'bn' ? disaster.nameBn : disaster.nameEn}
          </li>
        ))}
      </ul>
    </div>
  )
}

function radiusFor(camp: Camp): number {
  return Math.max(6, Math.min(16, 5 + Math.sqrt(camp.capacity) / 7))
}

function markerStyle(camp: Camp, color: string) {
  if (camp.status === 'CLOSED') {
    return { color: '#94a3b8', weight: 1.5, opacity: 0.8, fillColor: '#334155', fillOpacity: 0.3 }
  }
  return { color, weight: 1.5, opacity: 1, fillColor: color, fillOpacity: 0.55 }
}

function boundsOf(camps: Camp[]): LatLngBoundsExpression | undefined {
  if (camps.length === 0) return undefined
  return camps.map((camp) => [camp.lat, camp.lng] as [number, number])
}
