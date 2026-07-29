import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/auth/AuthContext'
import {
  approveProposal,
  fetchPendingProposals,
  rejectProposal,
  submitProposal,
  type ProposeInput,
} from '@/proposals/api'

export const PROPOSALS_QUERY_KEY = ['proposals'] as const

/** The Central Authority's pending queue. Not shared with any other reader (only that role ever
 * lists proposals), so it gets its own query rather than piggybacking on the world-read cache. */
export function usePendingProposals() {
  const { authFetch } = useAuth()
  return useQuery({
    queryKey: PROPOSALS_QUERY_KEY,
    queryFn: () => fetchPendingProposals(authFetch),
  })
}

/** Write actions below are plain async closures, not `useMutation` — matches every other
 * vertical slice in this codebase (see `admin/useAdminDisasters.ts`). */

export function useSubmitProposal() {
  const { authFetch } = useAuth()
  const queryClient = useQueryClient()
  return async (input: ProposeInput) => {
    const proposal = await submitProposal(authFetch, input)
    await queryClient.invalidateQueries({ queryKey: PROPOSALS_QUERY_KEY })
    return proposal
  }
}

export function useApproveProposal() {
  const { authFetch } = useAuth()
  const queryClient = useQueryClient()
  return async (id: number, reviewNote?: string) => {
    const proposal = await approveProposal(authFetch, id, reviewNote)
    await queryClient.invalidateQueries({ queryKey: PROPOSALS_QUERY_KEY })
    return proposal
  }
}

export function useRejectProposal() {
  const { authFetch } = useAuth()
  const queryClient = useQueryClient()
  return async (id: number, reviewNote?: string) => {
    const proposal = await rejectProposal(authFetch, id, reviewNote)
    await queryClient.invalidateQueries({ queryKey: PROPOSALS_QUERY_KEY })
    return proposal
  }
}
