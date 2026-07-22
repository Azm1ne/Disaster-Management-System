// Victim/family registration, dual-source arrival, and reunification search. Mirrors the shapes
// bd.dms.family.dto emits exactly (see FamilyIntegrationTest) — a fixture drawn from these types
// only proves the client agrees with itself, never with the API.

export type AgeBand = 'CHILD' | 'ADULT' | 'ELDER'
export type ArrivalStatus = 'REGISTERED' | 'ARRIVING' | 'ARRIVED'

export interface MemberInput {
  nickname: string
  ageBand: AgeBand
}

/** A roster row as seen by the group's own owner or the destination camp's staff — never reunification search. */
export interface MemberView extends MemberInput {
  id: number
  medicalFlag: boolean
}

export interface FamilyGroupStatus {
  id: number
  groupName: string
  campId: number
  campNameEn: string
  campNameBn: string
  memberCount: number
  representativeArrived: boolean
  managerConfirmedArrived: boolean
  status: ArrivalStatus
  members: MemberView[]
}

/** The reunification whitelist: group name, camp, status — nothing else, ever. */
export interface ReunificationResult {
  groupName: string
  campNameEn: string
  campNameBn: string
  status: ArrivalStatus
}

export interface CampArrivalGroup {
  id: number
  groupName: string
  memberCount: number
  representativeArrived: boolean
  managerConfirmedArrived: boolean
  status: ArrivalStatus
  members: MemberView[]
}

export interface CampArrivalsView {
  arrivingCount: number
  arrivedCount: number
  groups: CampArrivalGroup[]
}

type Fetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>
const JSON_HEADERS = { 'Content-Type': 'application/json' }

export async function fetchMyFamilyGroup(authFetch: Fetcher): Promise<FamilyGroupStatus | null> {
  const response = await authFetch('/api/family/me')
  if (response.status === 404) return null
  if (!response.ok) throw new Error(`family_me_failed_${response.status}`)
  return (await response.json()) as FamilyGroupStatus
}

export async function registerFamilyGroup(
  authFetch: Fetcher,
  request: { campId: number; groupName: string; members: MemberInput[] },
): Promise<FamilyGroupStatus> {
  const response = await authFetch('/api/family/register', {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify(request),
  })
  if (!response.ok) throw new Error(`family_register_failed_${response.status}`)
  return (await response.json()) as FamilyGroupStatus
}

export async function confirmMyArrival(authFetch: Fetcher): Promise<FamilyGroupStatus> {
  const response = await authFetch('/api/family/me/arrived', { method: 'POST' })
  if (!response.ok) throw new Error(`family_arrived_failed_${response.status}`)
  return (await response.json()) as FamilyGroupStatus
}

export async function searchFamilies(query: string): Promise<ReunificationResult[]> {
  const response = await fetch(`/api/public/family-search?q=${encodeURIComponent(query)}`)
  if (!response.ok) throw new Error(`family_search_failed_${response.status}`)
  return (await response.json()) as ReunificationResult[]
}

export async function fetchCampArrivals(authFetch: Fetcher, campId: number): Promise<CampArrivalsView> {
  const response = await authFetch(`/api/camp/${campId}/arrivals`)
  if (!response.ok) throw new Error(`camp_arrivals_failed_${response.status}`)
  return (await response.json()) as CampArrivalsView
}

export async function confirmGroupArrival(
  authFetch: Fetcher,
  campId: number,
  groupId: number,
): Promise<CampArrivalGroup> {
  const response = await authFetch(`/api/camp/${campId}/arrivals/${groupId}/confirm`, { method: 'POST' })
  if (!response.ok) throw new Error(`confirm_arrival_failed_${response.status}`)
  return (await response.json()) as CampArrivalGroup
}

export async function setMemberMedicalFlag(
  authFetch: Fetcher,
  campId: number,
  groupId: number,
  memberId: number,
  medicalFlag: boolean,
): Promise<void> {
  const response = await authFetch(`/api/camp/${campId}/arrivals/${groupId}/members/${memberId}/medical-flag`, {
    method: 'PATCH',
    headers: JSON_HEADERS,
    body: JSON.stringify({ medicalFlag }),
  })
  if (!response.ok) throw new Error(`medical_flag_failed_${response.status}`)
}
