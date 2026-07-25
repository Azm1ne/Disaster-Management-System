import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/auth/AuthContext'
import {
  donate,
  fetchFundsReport,
  fetchMyDonations,
  fetchMyImpact,
  procure,
  type ResourceType,
} from '@/funds/api'

/** The donor's own donation history, freshest first. No realtime topic exists for this (same
 * tradeoff ticket 07/08 made for forecasts/allocations) — a plain query, refetched after a
 * donation via cache invalidation. */
export function useMyDonations() {
  const { authFetch } = useAuth()
  return useQuery({ queryKey: ['funds', 'donations', 'mine'], queryFn: () => fetchMyDonations(authFetch) })
}

/** The donor's aggregated Donation → Camp impact view. */
export function useMyImpact() {
  const { authFetch } = useAuth()
  return useQuery({ queryKey: ['funds', 'donations', 'mine', 'impact'], queryFn: () => fetchMyImpact(authFetch) })
}

export function useDonate() {
  const { authFetch } = useAuth()
  const queryClient = useQueryClient()
  return async (disasterId: number, amount: number) => {
    await donate(authFetch, disasterId, amount)
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['funds', 'donations'] }),
    ])
  }
}

/** Coordinator/Admin's unaccounted-funds report. */
export function useFundsReport() {
  const { authFetch } = useAuth()
  return useQuery({ queryKey: ['funds', 'report'], queryFn: () => fetchFundsReport(authFetch) })
}

export function useProcure() {
  const { authFetch } = useAuth()
  const queryClient = useQueryClient()
  return async (disasterId: number, campId: number, resourceType: ResourceType, amount: number) => {
    await procure(authFetch, disasterId, campId, resourceType, amount)
    await queryClient.invalidateQueries({ queryKey: ['funds', 'report'] })
  }
}
