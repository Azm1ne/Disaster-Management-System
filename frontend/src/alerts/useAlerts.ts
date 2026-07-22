import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/auth/AuthContext'
import { refetchIntervalFor, useRealtime, useTopic } from '@/realtime/RealtimeProvider'
import { fetchAlerts, type AlertSummary } from '@/alerts/api'
import { fetchMe as fetchMeWorld } from '@/world/api'

/**
 * The alerts this signed-in user can see, kept live. Coordinator/Admin get every alert on
 * /topic/alerts; a Camp Manager additionally needs their own camp's /topic/camp/{id}/alerts,
 * since the coordinator topic is refused to them server-side.
 */
export function useAlerts(): AlertSummary[] | undefined {
  const { authFetch, user } = useAuth()
  const { connected } = useRealtime()
  const queryClient = useQueryClient()

  const alertsKey = ['alerts'] as const
  const alerts = useQuery({
    queryKey: alertsKey,
    queryFn: () => fetchAlerts(authFetch),
    refetchInterval: refetchIntervalFor(connected),
  })

  const me = useQuery({ queryKey: ['me'], queryFn: () => fetchMeWorld(authFetch) })
  const campId = me.data?.campIds?.[0]

  const invalidate = () => queryClient.invalidateQueries({ queryKey: alertsKey })

  useTopic(user?.role === 'CAMP_MANAGER' ? null : '/topic/alerts', invalidate)
  useTopic(campId === undefined ? null : `/topic/camp/${campId}/alerts`, invalidate)

  return alerts.data
}
