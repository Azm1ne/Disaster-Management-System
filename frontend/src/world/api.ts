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

/** One line of a camp's resource summary. The type is a code the UI translates. */
export interface ResourceView {
  type: 'WATER' | 'FOOD' | 'MEDICAL' | 'SHELTER'
  quantity: number
  unit: string
}

/** A camp's full state, as pushed on its own topic to those entitled to it. */
export interface CampDetail extends Camp {
  disaster: { id: number; nameEn: string; nameBn: string } | null
  resources: ResourceView[]
}

/** The signed-in caller, including the camps they manage (empty for roles that manage none). */
export interface Me {
  username: string
  role: string
  nameEn: string
  nameBn: string
  campIds: number[]
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

export async function fetchMe(authFetch: Fetcher): Promise<Me> {
  const response = await authFetch('/api/me')
  if (!response.ok) throw new Error(`me_read_failed_${response.status}`)
  return (await response.json()) as Me
}

export async function fetchCamp(authFetch: Fetcher, id: number): Promise<CampDetail> {
  const response = await authFetch(`/api/world/camps/${id}`)
  if (!response.ok) throw new Error(`camp_read_failed_${response.status}`)
  return (await response.json()) as CampDetail
}

export async function fetchPublicCamps(): Promise<LocatorCamp[]> {
  const response = await fetch('/api/public/camps')
  if (!response.ok) throw new Error(`locator_read_failed_${response.status}`)
  return (await response.json()) as LocatorCamp[]
}
