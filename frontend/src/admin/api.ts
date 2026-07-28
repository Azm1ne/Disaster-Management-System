// The ADMIN-only world-registration surface: declare/edit/close disasters, place affected areas
// and camps under them, and read the geometry-edit trail. Mirrors frontend/src/allocations/api.ts's
// shape/fetcher convention.
//
// Geometry wire format: `DisasterAdminController`'s request records declare `geometry` as a plain
// `@NotBlank String` (not a nested object) — the backend stores disaster/area geometry as GeoJSON
// text, same convention as the existing `AffectedArea.geometry` column. So every geometry a caller
// sends here is a `GeoJsonPolygon` object that gets `JSON.stringify`-ed into that string field,
// not a nested JSON object in the request body.

import type { GeoJsonPolygon } from '@/world/api'

export type GeometrySubjectType = 'DISASTER' | 'AFFECTED_AREA'

export interface DisasterAdminView {
  id: number
  code: string
  type: string
  status: string
  nameEn: string
  nameBn: string
  geometry: GeoJsonPolygon | null
}

export interface AffectedAreaAdminView {
  id: number
  disasterId: number
  nameEn: string
  nameBn: string
  geometry: GeoJsonPolygon
}

export interface CampAdminView {
  id: number
  disasterId: number
  code: string
  nameEn: string
  nameBn: string
  lat: number
  lng: number
  capacity: number
  population: number
  status: string
}

export interface GeometryHistoryView {
  id: number
  subjectId: number
  previousGeometry: GeoJsonPolygon | null
  newGeometry: GeoJsonPolygon
  actorUserId: number
  createdAt: string
}

export interface CreateDisasterRequest {
  code: string
  type: string
  nameEn: string
  nameBn: string
  geometry: GeoJsonPolygon
}

export interface UpdateDisasterRequest {
  nameEn?: string
  nameBn?: string
  geometry?: GeoJsonPolygon
}

export interface CreateAffectedAreaRequest {
  nameEn: string
  nameBn: string
  geometry: GeoJsonPolygon
}

export interface CreateCampRequest {
  code: string
  nameEn: string
  nameBn: string
  lat: number
  lng: number
  capacity: number
  initialPopulation: number
}

type Fetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>

export async function createDisaster(
  authFetch: Fetcher,
  request: CreateDisasterRequest,
): Promise<DisasterAdminView> {
  const response = await authFetch('/api/admin/disasters', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...request, geometry: JSON.stringify(request.geometry) }),
  })
  if (!response.ok) throw new Error(`disaster_create_failed_${response.status}`)
  return (await response.json()) as DisasterAdminView
}

export async function updateDisaster(
  authFetch: Fetcher,
  id: number,
  request: UpdateDisasterRequest,
): Promise<DisasterAdminView> {
  const response = await authFetch(`/api/admin/disasters/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      ...request,
      geometry: request.geometry ? JSON.stringify(request.geometry) : undefined,
    }),
  })
  if (!response.ok) throw new Error(`disaster_update_failed_${response.status}`)
  return (await response.json()) as DisasterAdminView
}

export async function closeDisaster(authFetch: Fetcher, id: number): Promise<DisasterAdminView> {
  const response = await authFetch(`/api/admin/disasters/${id}/close`, { method: 'POST' })
  if (!response.ok) throw new Error(`disaster_close_failed_${response.status}`)
  return (await response.json()) as DisasterAdminView
}

export async function createAffectedArea(
  authFetch: Fetcher,
  disasterId: number,
  request: CreateAffectedAreaRequest,
): Promise<AffectedAreaAdminView> {
  const response = await authFetch(`/api/admin/disasters/${disasterId}/affected-areas`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...request, geometry: JSON.stringify(request.geometry) }),
  })
  if (!response.ok) throw new Error(`affected_area_create_failed_${response.status}`)
  return (await response.json()) as AffectedAreaAdminView
}

export async function createCamp(
  authFetch: Fetcher,
  disasterId: number,
  request: CreateCampRequest,
): Promise<CampAdminView> {
  const response = await authFetch(`/api/admin/disasters/${disasterId}/camps`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!response.ok) throw new Error(`camp_create_failed_${response.status}`)
  return (await response.json()) as CampAdminView
}

export async function fetchGeometryHistory(
  authFetch: Fetcher,
  subjectType: GeometrySubjectType,
  subjectId: number,
): Promise<GeometryHistoryView[]> {
  const path =
    subjectType === 'DISASTER'
      ? `/api/admin/disasters/${subjectId}/geometry-history`
      : `/api/admin/affected-areas/${subjectId}/geometry-history`
  const response = await authFetch(path)
  if (!response.ok) throw new Error(`geometry_history_read_failed_${response.status}`)
  return (await response.json()) as GeometryHistoryView[]
}
