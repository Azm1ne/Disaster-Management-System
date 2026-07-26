import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/auth/AuthContext'
import { refetchIntervalFor, useRealtime } from '@/realtime/RealtimeProvider'
import {
  fetchAnomalies,
  reviewAnomaly,
  type AnomalyFlagView,
  type ReviewAnomalyRequest,
} from '@/anomalies/api'

/** Every anomaly flag the signed-in user is entitled to see, polled on the same cadence as the
 * realtime fallback — there is no dedicated anomaly STOMP topic, same tradeoff ticket 07 made
 * for forecasts and ticket 08 made for allocations. */
export function useAnomalies(): AnomalyFlagView[] | undefined {
  const { authFetch } = useAuth()
  const { connected } = useRealtime()

  const anomalies = useQuery({
    queryKey: ['anomalies'],
    queryFn: () => fetchAnomalies(authFetch),
    refetchInterval: refetchIntervalFor(connected),
  })

  return anomalies.data
}

/** Drives Confirm/Dismiss and refreshes the queue on success — a plain async function, not
 * useMutation, matching this codebase's other write paths (see useAllocations.ts's
 * useTransitionAllocation). */
export function useReviewAnomaly() {
  const { authFetch } = useAuth()
  const queryClient = useQueryClient()

  return async (id: number, request: ReviewAnomalyRequest) => {
    await reviewAnomaly(authFetch, id, request)
    await queryClient.invalidateQueries({ queryKey: ['anomalies'] })
  }
}
