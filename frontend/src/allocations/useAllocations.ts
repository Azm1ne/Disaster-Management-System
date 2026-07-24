import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/auth/AuthContext'
import { refetchIntervalFor, useRealtime } from '@/realtime/RealtimeProvider'
import {
  fetchAllocations,
  transitionAllocation,
  type AllocationSummary,
  type TransitionAllocationRequest,
} from '@/allocations/api'

/** Every allocation the signed-in user is entitled to see, polled on the same cadence as the
 * realtime fallback — there is no dedicated allocation STOMP topic, same tradeoff ticket 07 made
 * for forecasts. */
export function useAllocations(): AllocationSummary[] | undefined {
  const { authFetch } = useAuth()
  const { connected } = useRealtime()

  const allocations = useQuery({
    queryKey: ['allocations'],
    queryFn: () => fetchAllocations(authFetch),
    refetchInterval: refetchIntervalFor(connected),
  })

  return allocations.data
}

/** Drives Approve/Modify/Reject and refreshes the queue on success — a plain async function, not
 * useMutation, matching how the rest of this codebase's write paths are wired (see
 * AlertWorkspace.tsx's inline `void transition(...)` calls; there is no useMutation precedent to
 * follow here either). */
export function useTransitionAllocation() {
  const { authFetch } = useAuth()
  const queryClient = useQueryClient()

  return async (id: number, request: TransitionAllocationRequest) => {
    await transitionAllocation(authFetch, id, request)
    await queryClient.invalidateQueries({ queryKey: ['allocations'] })
  }
}
