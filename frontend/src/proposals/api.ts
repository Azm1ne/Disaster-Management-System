// The proposal/approval slice shared by the Coordinator (files proposals) and the Central
// Authority (reviews them). Mirrors frontend/src/admin/api.ts's shape/fetcher convention.
//
// Geometry wire format: exactly like admin/api.ts — the backend's `ProposalController` accepts
// `payload` as a JSON object (`JsonNode`) and re-serializes it to text for storage, then that
// text is deserialized straight into the same `CreateDisasterRequest`/`UpdateDisasterRequest`/
// `CreateAffectedAreaRequest` Java records the direct admin endpoints use. Those records declare
// `geometry` as a plain string, so within the payload object, `geometry` must itself be a
// `JSON.stringify`-ed string, not a nested object — the same detail Task 7 had to handle, just
// one level further in.

import type {
  CreateAffectedAreaRequest,
  CreateCampRequest,
  CreateDisasterRequest,
  UpdateDisasterRequest,
} from '@/admin/api'

export type ProposalType =
  | 'DISASTER_CREATE'
  | 'DISASTER_UPDATE'
  | 'DISASTER_CLOSE'
  | 'AFFECTED_AREA_CREATE'
  | 'CAMP_CREATE'

export type ProposalStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface Proposal {
  id: number
  proposalType: ProposalType
  targetDisasterId: number | null
  payload: Record<string, unknown>
  status: ProposalStatus
  proposedByUserId: number
  createdAt: string
  reviewedByUserId: number | null
  reviewedAt: string | null
  reviewNote: string | null
}

/** What a coordinator submits for each proposal type — one variant per `ProposalType`, each
 * pairing the target disaster (nullable only for a brand-new disaster) with the exact admin
 * request shape `DisasterAdminService` expects once approved. */
export type ProposeInput =
  | { proposalType: 'DISASTER_CREATE'; targetDisasterId: null; payload: CreateDisasterRequest }
  | { proposalType: 'DISASTER_UPDATE'; targetDisasterId: number; payload: UpdateDisasterRequest }
  | { proposalType: 'DISASTER_CLOSE'; targetDisasterId: number; payload: Record<string, never> }
  | { proposalType: 'AFFECTED_AREA_CREATE'; targetDisasterId: number; payload: CreateAffectedAreaRequest }
  | { proposalType: 'CAMP_CREATE'; targetDisasterId: number; payload: CreateCampRequest }

type Fetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>

/** Stringifies the `geometry` field(s) a proposal's payload carries, same as admin/api.ts. */
function serializePayload(input: ProposeInput): object {
  switch (input.proposalType) {
    case 'DISASTER_CREATE':
    case 'AFFECTED_AREA_CREATE':
      return { ...input.payload, geometry: JSON.stringify(input.payload.geometry) }
    case 'DISASTER_UPDATE':
      return {
        ...input.payload,
        geometry: input.payload.geometry ? JSON.stringify(input.payload.geometry) : undefined,
      }
    case 'DISASTER_CLOSE':
    case 'CAMP_CREATE':
      return input.payload
  }
}

export async function fetchPendingProposals(authFetch: Fetcher): Promise<Proposal[]> {
  const response = await authFetch('/api/proposals')
  if (!response.ok) throw new Error(`proposals_read_failed_${response.status}`)
  return (await response.json()) as Proposal[]
}

export async function submitProposal(authFetch: Fetcher, input: ProposeInput): Promise<Proposal> {
  const response = await authFetch('/api/proposals', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      proposalType: input.proposalType,
      targetDisasterId: input.targetDisasterId,
      payload: serializePayload(input),
    }),
  })
  if (!response.ok) throw new Error(`proposal_submit_failed_${response.status}`)
  return (await response.json()) as Proposal
}

export async function approveProposal(
  authFetch: Fetcher,
  id: number,
  reviewNote?: string,
): Promise<Proposal> {
  const response = await authFetch(`/api/proposals/${id}/approve`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reviewNote }),
  })
  if (!response.ok) throw new Error(`proposal_approve_failed_${response.status}`)
  return (await response.json()) as Proposal
}

export async function rejectProposal(
  authFetch: Fetcher,
  id: number,
  reviewNote?: string,
): Promise<Proposal> {
  const response = await authFetch(`/api/proposals/${id}/reject`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reviewNote }),
  })
  if (!response.ok) throw new Error(`proposal_reject_failed_${response.status}`)
  return (await response.json()) as Proposal
}
