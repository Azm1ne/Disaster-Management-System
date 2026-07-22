import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/auth/AuthContext'
import { refetchIntervalFor, useRealtime, useTopic } from '@/realtime/RealtimeProvider'
import { fetchDisasters, type Disaster } from '@/world/api'

export const DISASTERS_QUERY_KEY = ['world', 'disasters'] as const

type State =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'ready'; disasters: Disaster[] }

/**
 * The live world. The simulation pushes each tick over STOMP straight into the query cache, so
 * the map redraws without refetching; if the socket drops, the same query falls back to polling
 * until it returns. Callers see one shape either way and never learn which path delivered it.
 */
export function useDisasters(): State {
  const { authFetch } = useAuth()
  const { connected } = useRealtime()
  const queryClient = useQueryClient()

  const query = useQuery({
    queryKey: DISASTERS_QUERY_KEY,
    queryFn: () => fetchDisasters(authFetch),
    refetchInterval: refetchIntervalFor(connected),
  })

  useTopic('/topic/world', (body) => {
    queryClient.setQueryData(DISASTERS_QUERY_KEY, body as Disaster[])
  })

  if (query.isPending) return { status: 'loading' }
  if (query.isError || !query.data) return { status: 'error' }
  return { status: 'ready', disasters: query.data }
}
