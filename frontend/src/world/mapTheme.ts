import type { DisasterType, GeoJsonPolygon } from '@/world/api'

// The two disasters read as distinct color-worlds on one map: the Jamuna flood in cool cyan
// (water), the Patuakhali cyclone in warm amber (storm). These are concrete colors, not CSS
// tokens, because Leaflet paints vector paths with plain color strings.
export const DISASTER_COLOR: Record<DisasterType, string> = {
  FLOOD: '#4cc9f0',
  CYCLONE: '#f6a821',
}

// CARTO basemaps are OpenStreetMap data restyled — dark for the operator situation room,
// light (Positron) for the field/public locator. No API key required; attribution is required.
const OSM_CARTO_ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>'

export const TILES = {
  dark: {
    url: 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png',
    attribution: OSM_CARTO_ATTRIBUTION,
  },
  light: {
    url: 'https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png',
    attribution: OSM_CARTO_ATTRIBUTION,
  },
} as const

// The whole Jamuna + Patuakhali stage, so a cold map open frames both disasters at once.
export const BANGLADESH_DELTA_CENTER: [number, number] = [23.9, 90.0]

/** GeoJSON stores rings as [lng, lat]; Leaflet wants [lat, lng]. Flip each vertex. */
export function toLeafletRings(geometry: GeoJsonPolygon): [number, number][][] {
  return geometry.coordinates.map((ring) => ring.map(([lng, lat]) => [lat, lng]))
}
