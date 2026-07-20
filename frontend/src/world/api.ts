// The shapes the world-read API returns, and thin fetchers for them. Authenticated reads go
// through authFetch (bearer + refresh-on-401); the public locator is a plain, no-login fetch.

export type DisasterType = 'FLOOD' | 'CYCLONE'
export type CampStatus = 'OPEN' | 'CLOSED'

/** A GeoJSON Polygon: rings of [lng, lat] pairs, exactly as stored server-side. */
export interface GeoJsonPolygon {
  type: 'Polygon'
  coordinates: number[][][]
}

export interface AffectedArea {
  id: number
  nameEn: string
  nameBn: string
  geometry: GeoJsonPolygon
}

export interface Camp {
  id: number
  code: string
  nameEn: string
  nameBn: string
  lat: number
  lng: number
  capacity: number
  population: number
  status: CampStatus
}

export interface Disaster {
  id: number
  code: string
  type: DisasterType
  status: string
  nameEn: string
  nameBn: string
  affectedAreas: AffectedArea[]
  camps: Camp[]
}

/** The public locator's deliberately narrow camp shape — name, location, and status only. */
export interface LocatorCamp {
  id: number
  nameEn: string
  nameBn: string
  lat: number
  lng: number
  status: CampStatus
}

type Fetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>

export async function fetchDisasters(authFetch: Fetcher): Promise<Disaster[]> {
  const response = await authFetch('/api/world/disasters')
  if (!response.ok) throw new Error(`world_read_failed_${response.status}`)
  return (await response.json()) as Disaster[]
}

export async function fetchPublicCamps(): Promise<LocatorCamp[]> {
  const response = await fetch('/api/public/camps')
  if (!response.ok) throw new Error(`locator_read_failed_${response.status}`)
  return (await response.json()) as LocatorCamp[]
}
