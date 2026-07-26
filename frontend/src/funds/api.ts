// The money-model surface: shapes returned by /funds/**, and thin fetchers over authFetch —
// mirrors frontend/src/allocations/api.ts.

export type ResourceType = 'WATER' | 'FOOD' | 'MEDICAL'

export interface DonationView {
  id: number
  disasterId: number
  disasterNameEn: string
  disasterNameBn: string
  amount: number
  createdAt: string
}

export interface ProcurementView {
  id: number
  disasterId: number
  campId: number
  campNameEn: string
  campNameBn: string
  resourceType: ResourceType
  amount: number
  unitPrice: number
  quantity: number
  createdAt: string
}

export interface CampImpact {
  campId: number
  campNameEn: string
  campNameBn: string
  resourceType: ResourceType
  amountProcured: number
  quantityProcured: number
}

export interface DisasterImpact {
  disasterId: number
  nameEn: string
  nameBn: string
  donatedByMe: number
  camps: CampImpact[]
}

export interface DonorImpactView {
  disasters: DisasterImpact[]
  totalDonatedByMe: number
}

export interface DisasterFundSummary {
  disasterId: number
  nameEn: string
  nameBn: string
  donated: number
  procured: number
  unaccounted: number
}

export interface FundsReport {
  disasters: DisasterFundSummary[]
  totalDonated: number
  totalProcured: number
  totalUnaccounted: number
}

type Fetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>

export async function fetchMyDonations(authFetch: Fetcher): Promise<DonationView[]> {
  const response = await authFetch('/api/funds/donations/mine')
  if (!response.ok) throw new Error(`donations_read_failed_${response.status}`)
  return (await response.json()) as DonationView[]
}

export async function fetchMyImpact(authFetch: Fetcher): Promise<DonorImpactView> {
  const response = await authFetch('/api/funds/donations/mine/impact')
  if (!response.ok) throw new Error(`impact_read_failed_${response.status}`)
  return (await response.json()) as DonorImpactView
}

export async function donate(authFetch: Fetcher, disasterId: number, amount: number): Promise<DonationView> {
  const response = await authFetch('/api/funds/donations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ disasterId, amount }),
  })
  if (!response.ok) throw new Error(`donate_failed_${response.status}`)
  return (await response.json()) as DonationView
}

export async function fetchFundsReport(authFetch: Fetcher): Promise<FundsReport> {
  const response = await authFetch('/api/funds/report')
  if (!response.ok) throw new Error(`funds_report_failed_${response.status}`)
  return (await response.json()) as FundsReport
}

export async function procure(
  authFetch: Fetcher,
  disasterId: number,
  campId: number,
  resourceType: ResourceType,
  amount: number,
): Promise<ProcurementView> {
  const response = await authFetch('/api/funds/procurements', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ disasterId, campId, resourceType, amount }),
  })
  if (!response.ok) throw new Error(`procure_failed_${response.status}`)
  return (await response.json()) as ProcurementView
}
