import { expect, test } from 'vitest'
import L from 'leaflet'
import { ringToGeoJsonPolygon } from '@/world/DisasterDrawMap'

// Leaflet's MapContainer needs real layout/canvas machinery this project's jsdom test
// environment doesn't provide (no other test in this codebase renders a react-leaflet map —
// see MyCampPanel.test.tsx for the established pattern of testing the surrounding logic
// instead). ringToGeoJsonPolygon is the one piece of DisasterDrawMap that's pure and
// deterministic, so it's what's covered here; the draw/click wiring is exercised manually.

test('converts an open Leaflet ring to a closed GeoJSON [lng, lat] polygon', () => {
  const ring = [
    L.latLng(24.0, 89.5),
    L.latLng(24.5, 89.5),
    L.latLng(24.5, 90.0),
  ]

  const geometry = ringToGeoJsonPolygon(ring)

  expect(geometry).toEqual({
    type: 'Polygon',
    coordinates: [
      [
        [89.5, 24.0],
        [89.5, 24.5],
        [90.0, 24.5],
        [89.5, 24.0],
      ],
    ],
  })
})

test('does not double-close a ring Leaflet already closed', () => {
  const ring = [
    L.latLng(24.0, 89.5),
    L.latLng(24.5, 89.5),
    L.latLng(24.5, 90.0),
    L.latLng(24.0, 89.5),
  ]

  const geometry = ringToGeoJsonPolygon(ring)

  expect(geometry.coordinates[0]).toHaveLength(4)
})

test('round-trips through the inverse Leaflet-ring conversion used to redraw stored geometry', () => {
  const ring = [L.latLng(24.0, 89.5), L.latLng(24.5, 89.5), L.latLng(24.5, 90.0)]

  const geometry = ringToGeoJsonPolygon(ring)
  const leafletPairs = geometry.coordinates[0].map(([lng, lat]) => [lat, lng])

  expect(leafletPairs[0]).toEqual([24.0, 89.5])
  expect(leafletPairs[1]).toEqual([24.5, 89.5])
  expect(leafletPairs[2]).toEqual([24.5, 90.0])
})
