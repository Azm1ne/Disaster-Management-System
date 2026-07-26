// Anomaly review queue: shapes returned by /anomalies, and thin fetchers over authFetch —
// mirrors frontend/src/allocations/api.ts.

export type AnomalyDetectorType = 'ALLOCATION_BURST' | 'DUPLICATE_REGISTRATION' | 'DONATION_PATTERN'
export type AnomalyFlagStatus = 'OPEN' | 'CONFIRMED' | 'DISMISSED'

export interface AnomalyFlagView {
  id: number
  detectorType: AnomalyDetectorType
  score: number
  summary: string
  innocentExplanation: string
  subjectIds: number[]
  detectedAtTick: number | null
  status: AnomalyFlagStatus
  reviewedByUserId: number | null
  reviewNote: string | null
  reviewedAt: string | null
  createdAt: string
}

export interface ReviewAnomalyRequest {
  toStatus: 'CONFIRMED' | 'DISMISSED'
  note?: string
}

type Fetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>

export async function fetchAnomalies(authFetch: Fetcher): Promise<AnomalyFlagView[]> {
  const response = await authFetch('/api/anomalies')
  if (!response.ok) throw new Error(`anomalies_read_failed_${response.status}`)
  return (await response.json()) as AnomalyFlagView[]
}

export async function reviewAnomaly(
  authFetch: Fetcher,
  id: number,
  request: ReviewAnomalyRequest,
): Promise<AnomalyFlagView> {
  const response = await authFetch(`/api/anomalies/${id}/review`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!response.ok) throw new Error(`anomaly_review_failed_${response.status}`)
  return (await response.json()) as AnomalyFlagView
}
