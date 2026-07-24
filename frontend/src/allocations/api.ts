// Allocation decision queue: shapes returned by /allocations, and thin fetchers over authFetch —
// mirrors frontend/src/forecasts/api.ts and frontend/src/alerts/api.ts.

export type AllocationResourceType = 'WATER' | 'FOOD' | 'MEDICAL'
export type AllocationStatus = 'RECOMMENDED' | 'APPROVED' | 'MODIFIED' | 'REJECTED'

export interface AllocationSummary {
  id: number
  resourceType: AllocationResourceType
  sourceCampId: number
  targetCampId: number
  recommendedQuantity: number
  decidedQuantity: number | null
  status: AllocationStatus
  severityScore: number
  shortageScore: number
  populationScore: number
  fairnessScore: number
  priorityScore: number
  generatedAtTick: number
  decidedAtTick: number | null
  canAct: boolean
  createdAt: string
  updatedAt: string
}

export interface TransitionAllocationRequest {
  toStatus: 'APPROVED' | 'MODIFIED' | 'REJECTED'
  quantity?: number
  note?: string
}

type Fetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>

export async function fetchAllocations(authFetch: Fetcher): Promise<AllocationSummary[]> {
  const response = await authFetch('/api/allocations')
  if (!response.ok) throw new Error(`allocations_read_failed_${response.status}`)
  return (await response.json()) as AllocationSummary[]
}

export async function transitionAllocation(
  authFetch: Fetcher,
  id: number,
  request: TransitionAllocationRequest,
): Promise<AllocationSummary> {
  const response = await authFetch(`/api/allocations/${id}/transition`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!response.ok) throw new Error(`allocation_transition_failed_${response.status}`)
  return (await response.json()) as AllocationSummary
}
