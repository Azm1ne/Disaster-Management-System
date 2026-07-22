import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/auth/AuthContext'
import { refetchIntervalFor, useRealtime, useTopic } from '@/realtime/RealtimeProvider'
import {
  addAlertNote,
  fetchAlertDetail,
  transitionAlert,
  type AlertStatus,
} from '@/alerts/api'

/** One alert's full detail (transitions + notes), plus the two actions its UI needs. */
export function useAlertDetail(id: number | null) {
  const { authFetch, user } = useAuth()
  const { connected } = useRealtime()
  const queryClient = useQueryClient()

  const key = ['alerts', id] as const
  const detail = useQuery({
    queryKey: key,
    queryFn: () => fetchAlertDetail(authFetch, id as number),
    enabled: id !== null,
    refetchInterval: refetchIntervalFor(connected),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: key })
  useTopic(id === null || user?.role === 'CAMP_MANAGER' ? null : '/topic/alerts', invalidate)

  return {
    detail: detail.data,
    transition: (toStatus: AlertStatus, note?: string) =>
      transitionAlert(authFetch, id as number, toStatus, note).then(invalidate),
    addNote: (body: string) => addAlertNote(authFetch, id as number, body).then(invalidate),
  }
}
