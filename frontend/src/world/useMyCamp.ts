import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/auth/AuthContext'
import { refetchIntervalFor, useRealtime, useTopic } from '@/realtime/RealtimeProvider'
import { fetchCamp, fetchMe, type CampDetail } from '@/world/api'

/**
 * The camp this user manages, kept live on its own topic. The server only lets a manager
 * subscribe to a camp they are actually assigned to, so this is the entitled half of the
 * per-camp boundary: the same subscription from anyone else simply never delivers.
 *
 * <p>Returns undefined for users who manage no camp.
 */
export function useMyCamp(): CampDetail | undefined {
  const { authFetch } = useAuth()
  const { connected } = useRealtime()
  const queryClient = useQueryClient()

  const me = useQuery({ queryKey: ['me'], queryFn: () => fetchMe(authFetch) })
  const campId = me.data?.campIds?.[0]

  const campKey = ['world', 'camp', campId] as const
  const camp = useQuery({
    queryKey: campKey,
    queryFn: () => fetchCamp(authFetch, campId as number),
    enabled: campId !== undefined,
    refetchInterval: refetchIntervalFor(connected),
  })

  useTopic(campId === undefined ? null : `/topic/camp/${campId}`, (body) => {
    queryClient.setQueryData(campKey, body as CampDetail)
  })

  return camp.data
}
