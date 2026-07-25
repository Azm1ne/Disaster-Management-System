// Volunteer matching: shapes returned by /volunteers/**, and thin fetchers over authFetch —
// mirrors frontend/src/allocations/api.ts.

export type Skill = 'MEDICAL' | 'LOGISTICS' | 'SECURITY' | 'ENGINEERING'
export type VolunteerTaskStatus = 'OPEN' | 'ASSIGNED' | 'CANCELLED'
export type AssignmentMethod = 'PUSH' | 'SELF'

export interface VolunteerTaskSummary {
  id: number
  alertId: number
  campId: number
  requiredSkill: Skill
  description: string
  status: VolunteerTaskStatus
  assignedVolunteerId: number | null
  assignedVolunteerNameEn: string | null
  assignedVolunteerNameBn: string | null
  assignmentMethod: AssignmentMethod | null
  urgencyScore: number
  generatedAtTick: number
  assignedAtTick: number | null
  canAssign: boolean
  canAccept: boolean
  createdAt: string
  updatedAt: string
}

export interface VolunteerCandidate {
  volunteerId: number
  nameEn: string
  nameBn: string
  hasSkill: boolean
  distanceKm: number
  skillScore: number
  distanceScore: number
  urgencyScore: number
  score: number
}

export interface SkillCoverage {
  skill: Skill
  openTaskCount: number
  availableVolunteerCount: number
  gap: number
  unmet: boolean
}

export interface RouteView {
  points: [number, number][]
  distanceMeters: number
  durationSeconds: number
  source: 'OSRM' | 'STRAIGHT_LINE'
}

type Fetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>

export async function fetchVolunteerTasks(authFetch: Fetcher): Promise<VolunteerTaskSummary[]> {
  const response = await authFetch('/api/volunteers/tasks')
  if (!response.ok) throw new Error(`volunteer_tasks_read_failed_${response.status}`)
  return (await response.json()) as VolunteerTaskSummary[]
}

export async function fetchMyAssignments(authFetch: Fetcher): Promise<VolunteerTaskSummary[]> {
  const response = await authFetch('/api/volunteers/tasks/mine')
  if (!response.ok) throw new Error(`volunteer_mine_read_failed_${response.status}`)
  return (await response.json()) as VolunteerTaskSummary[]
}

export async function fetchCandidates(authFetch: Fetcher, taskId: number): Promise<VolunteerCandidate[]> {
  const response = await authFetch(`/api/volunteers/tasks/${taskId}/candidates`)
  if (!response.ok) throw new Error(`volunteer_candidates_read_failed_${response.status}`)
  return (await response.json()) as VolunteerCandidate[]
}

export async function assignVolunteer(
  authFetch: Fetcher,
  taskId: number,
  volunteerId: number,
  note?: string,
): Promise<VolunteerTaskSummary> {
  const response = await authFetch(`/api/volunteers/tasks/${taskId}/assign`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ volunteerId, note }),
  })
  if (!response.ok) throw new Error(`volunteer_assign_failed_${response.status}`)
  return (await response.json()) as VolunteerTaskSummary
}

export async function acceptShift(authFetch: Fetcher, taskId: number): Promise<VolunteerTaskSummary> {
  const response = await authFetch(`/api/volunteers/tasks/${taskId}/accept`, { method: 'POST' })
  if (!response.ok) throw new Error(`volunteer_accept_failed_${response.status}`)
  return (await response.json()) as VolunteerTaskSummary
}

export async function fetchRoute(authFetch: Fetcher, taskId: number): Promise<RouteView> {
  const response = await authFetch(`/api/volunteers/tasks/${taskId}/route`)
  if (!response.ok) throw new Error(`volunteer_route_read_failed_${response.status}`)
  return (await response.json()) as RouteView
}

export async function fetchSkillGap(authFetch: Fetcher): Promise<SkillCoverage[]> {
  const response = await authFetch('/api/volunteers/skill-gap')
  if (!response.ok) throw new Error(`volunteer_skill_gap_read_failed_${response.status}`)
  return (await response.json()) as SkillCoverage[]
}
