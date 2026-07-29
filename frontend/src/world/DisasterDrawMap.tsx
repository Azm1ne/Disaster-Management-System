import 'leaflet/dist/leaflet.css'
import 'leaflet-draw/dist/leaflet.draw.css'
import { Fragment, useEffect, useRef, type RefObject } from 'react'
import L from 'leaflet'
import 'leaflet-draw'
import { useTranslation } from 'react-i18next'
import { CircleMarker, MapContainer, Polygon, TileLayer, useMap } from 'react-leaflet'
import type { Disaster, GeoJsonPolygon } from '@/world/api'
import { BANGLADESH_DELTA_CENTER, TILES, toLeafletRings } from '@/world/mapTheme'
import { Hexagon, MapPinPlus } from 'lucide-react'

export type DrawMode = 'polygon' | 'point'

/**
 * Converts the ring a user just drew (Leaflet lat/lng order) into the GeoJSON `[lng, lat]`
 * ring shape the backend stores. Closes the ring (repeats the first vertex) to match the
 * convention already used by the seeded disaster/affected-area geometry.
 */
export function ringToGeoJsonPolygon(ring: L.LatLng[]): GeoJsonPolygon {
  const coords = ring.map(({ lat, lng }): [number, number] => [lng, lat])
  const first = coords[0]
  const last = coords[coords.length - 1]
  const closed = first[0] === last[0] && first[1] === last[1] ? coords : [...coords, first]
  return { type: 'Polygon', coordinates: [closed] }
}

/**
 * The shared draw/place primitive behind both the Admin declare/edit page and the Coordinator
 * propose page (Tasks 7-8). Renders the same dark world map as `WorldMap`, with existing
 * disasters/areas/camps shown as a dimmed, non-interactive backdrop for spatial context, plus
 * one active interaction mode: draw a boundary polygon, or click to place a point.
 *
 * Editing an existing polygon is redraw-from-scratch: `editingGeometry` only pre-loads the
 * shape as a visual reference, it is not vertex-editable.
 */
export function DisasterDrawMap({
  disasters,
  mode,
  editingGeometry = null,
  onPolygonDrawn,
  onPointPlaced,
}: {
  disasters: Disaster[]
  mode: DrawMode
  editingGeometry?: GeoJsonPolygon | null
  onPolygonDrawn: (geometry: GeoJsonPolygon) => void
  onPointPlaced: (lat: number, lng: number) => void
}) {
  const { t } = useTranslation()
  const cancelRef = useRef<(() => void) | null>(null)

  return (
    <div className="relative h-full w-full overflow-hidden">
      <MapContainer
        center={BANGLADESH_DELTA_CENTER}
        zoom={7}
        zoomControl={false}
        className="h-full w-full bg-bg"
        aria-label={t('disasterDraw.mapLabel')}
      >
        <TileLayer url={TILES.dark.url} attribution={TILES.dark.attribution} />

        <Backdrop disasters={disasters} />

        {editingGeometry && (
          <Polygon
            positions={toLeafletRings(editingGeometry)}
            pathOptions={{ color: '#f6a821', weight: 2, opacity: 0.9, fillOpacity: 0.1, dashArray: '2 6' }}
            interactive={false}
          />
        )}

        <DrawController
          mode={mode}
          onPolygonDrawn={onPolygonDrawn}
          onPointPlaced={onPointPlaced}
          cancelRef={cancelRef}
        />
      </MapContainer>

      <ModeStatus mode={mode} onCancel={() => cancelRef.current?.()} />
    </div>
  )
}

/** Existing world state, muted, underneath the drawing surface — for spatial orientation only. */
function Backdrop({ disasters }: { disasters: Disaster[] }) {
  return (
    <>
      {disasters.map((disaster) => (
        <Fragment key={disaster.id}>
          {disaster.affectedAreas.map((area) => (
            <Polygon
              key={`area-${area.id}`}
              positions={toLeafletRings(area.geometry)}
              pathOptions={{ color: '#64748b', weight: 1, opacity: 0.4, fillOpacity: 0.04, dashArray: '3 5' }}
              interactive={false}
            />
          ))}
          {disaster.camps.map((camp) => (
            <CircleMarker
              key={`camp-${camp.id}`}
              center={[camp.lat, camp.lng]}
              radius={4}
              pathOptions={{ color: '#64748b', weight: 1, opacity: 0.5, fillColor: '#64748b', fillOpacity: 0.3 }}
              interactive={false}
            />
          ))}
        </Fragment>
      ))}
    </>
  )
}

/**
 * Imperative leaflet-draw integration. react-leaflet v5 has no official draw bindings, so this
 * drives the underlying Leaflet map instance directly via `useMap()`, per leaflet-draw's
 * standard non-React integration pattern. No stock `L.Control.Draw` toolbar is added — `mode`
 * is fully controlled by the caller, so the on-map UI is limited to leaflet-draw's in-progress
 * drawing feedback (guide line, vertex tooltip), restyled to match the app's dark theme.
 */
function DrawController({
  mode,
  onPolygonDrawn,
  onPointPlaced,
  cancelRef,
}: {
  mode: DrawMode
  onPolygonDrawn: (geometry: GeoJsonPolygon) => void
  onPointPlaced: (lat: number, lng: number) => void
  cancelRef: RefObject<(() => void) | null>
}) {
  const map = useMap()

  useEffect(() => {
    if (mode !== 'polygon') return

    const handler = new L.Draw.Polygon(map as unknown as L.DrawMap, {
      shapeOptions: { color: '#f6a821', weight: 2, fillOpacity: 0.1 },
      showArea: false,
    })
    handler.enable()
    // Restarting a fresh, empty drawing is the cancel affordance: discards whatever vertices
    // were placed so far without leaving polygon mode.
    cancelRef.current = () => {
      handler.disable()
      handler.enable()
    }

    const onCreated = (event: L.LeafletEvent) => {
      const { layer } = event as L.DrawEvents.Created
      const rings = (layer as L.Polygon).getLatLngs() as L.LatLng[][]
      onPolygonDrawn(ringToGeoJsonPolygon(rings[0]))
      // Stay in polygon mode until the caller switches away: re-arm for the next draw so an
      // "edit" flow's redraw-from-scratch doesn't need an explicit re-enable step.
      handler.enable()
    }
    map.on(L.Draw.Event.CREATED, onCreated)

    return () => {
      map.off(L.Draw.Event.CREATED, onCreated)
      handler.disable()
      cancelRef.current = null
    }
  }, [map, mode, onPolygonDrawn, cancelRef])

  useEffect(() => {
    if (mode !== 'point') return

    const onClick = (event: L.LeafletMouseEvent) => onPointPlaced(event.latlng.lat, event.latlng.lng)
    map.on('click', onClick)

    return () => {
      map.off('click', onClick)
    }
  }, [map, mode, onPointPlaced])

  return null
}

function ModeStatus({ mode, onCancel }: { mode: DrawMode; onCancel: () => void }) {
  const { t } = useTranslation()

  return (
    <div className="absolute top-3 right-3 z-[1000] rounded-lg border border-line bg-surface/90 px-3 py-2.5 backdrop-blur">
      <p className="flex items-center gap-1.5 font-mono text-[10px] tracking-[0.16em] text-ink-muted uppercase">
        {mode === 'polygon' ? <Hexagon className="h-3 w-3" aria-hidden /> : <MapPinPlus className="h-3 w-3" aria-hidden />}
        {t(mode === 'polygon' ? 'disasterDraw.polygonMode' : 'disasterDraw.pointMode')}
      </p>
      <p className="mt-1 max-w-56 text-xs text-ink">
        {t(mode === 'polygon' ? 'disasterDraw.polygonHint' : 'disasterDraw.pointHint')}
      </p>
      {mode === 'polygon' && (
        <button
          type="button"
          onClick={onCancel}
          className="mt-2 text-xs font-medium text-ink-muted underline decoration-dotted underline-offset-2 hover:text-ink"
        >
          {t('disasterDraw.cancel')}
        </button>
      )}
    </div>
  )
}
