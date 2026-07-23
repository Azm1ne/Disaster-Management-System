// Explainable resource forecasts: shapes returned by /forecasts, and a thin fetcher over
// authFetch — mirrors frontend/src/world/api.ts and frontend/src/alerts/api.ts.

export type ForecastResourceType = 'WATER' | 'FOOD' | 'MEDICAL'
export type ConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW'

export interface ForecastView {
  campId: number
  campCode: string
  campNameEn: string
  campNameBn: string
  resourceType: ForecastResourceType
  currentQuantity: number
  ratePerTick: number
  ticksRemainingEstimate: number | null
  ticksRemainingWorstCase: number | null
  ticksRemainingBestCase: number | null
  confidenceScore: number
  confidenceLevel: ConfidenceLevel
  latestObservedTick: number
  sampleCount: number
}

type Fetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>

export async function fetchForecasts(authFetch: Fetcher): Promise<ForecastView[]> {
  const response = await authFetch('/api/forecasts')
  if (!response.ok) throw new Error(`forecasts_read_failed_${response.status}`)
  return (await response.json()) as ForecastView[]
}
