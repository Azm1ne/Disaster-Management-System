import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/auth/AuthContext'
import { refetchIntervalFor, useRealtime, useTopic } from '@/realtime/RealtimeProvider'
import { fetchClock, type SimulationClock } from '@/sim/api'

export const CLOCK_QUERY_KEY = ['simulation', 'clock'] as const

/**
 * The shared DEMO clock. Seeded by a read and then kept in step by pushes on
 * {@code /topic/simulation}, so every screen shows the same simulated moment; it falls back to
 * polling whenever the socket is down.
 */
export function useSimulationClock(): SimulationClock | undefined {
  const { authFetch } = useAuth()
  const { connected } = useRealtime()
  const queryClient = useQueryClient()

  const query = useQuery({
    queryKey: CLOCK_QUERY_KEY,
    queryFn: () => fetchClock(authFetch),
    refetchInterval: refetchIntervalFor(connected),
  })

  useTopic('/topic/simulation', (body) => {
    queryClient.setQueryData(CLOCK_QUERY_KEY, body as SimulationClock)
  })

  return query.data
}
