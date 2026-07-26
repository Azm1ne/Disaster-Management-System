import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/auth/AuthContext'
import { refetchIntervalFor, useRealtime, useTopic } from '@/realtime/RealtimeProvider'
import {
  fetchBroadcasts,
  markBroadcastRead,
  sendBroadcast,
  type BroadcastTargetRole,
  type BroadcastView,
} from '@/comms/api'

const RECIPIENT_ROLES: ReadonlySet<string> = new Set(['CAMP_MANAGER', 'VOLUNTEER'])

/** Broadcasts visible to the signed-in user: a Coordinator/Admin sees everything sent, a Camp
 * Manager/Volunteer sees only announcements targeted at their own role, pushed live over
 * `/topic/broadcasts/<role>` with the poll interval as fallback (mirrors `useAlertDetail`). */
export function useBroadcasts(): {
  broadcasts: BroadcastView[] | undefined
  send: (input: { targetRole: BroadcastTargetRole; bodyEn: string; bodyBn: string }) => Promise<void>
  markRead: (id: number) => Promise<void>
} {
  const { authFetch, user } = useAuth()
  const { connected } = useRealtime()
  const queryClient = useQueryClient()
  const key = ['broadcasts'] as const

  const broadcasts = useQuery({
    queryKey: key,
    queryFn: () => fetchBroadcasts(authFetch),
    refetchInterval: refetchIntervalFor(connected),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: key })
  const ownRoleTopic = user && RECIPIENT_ROLES.has(user.role) ? `/topic/broadcasts/${user.role}` : null
  useTopic(ownRoleTopic, invalidate)

  return {
    broadcasts: broadcasts.data,
    send: (input) => sendBroadcast(authFetch, input).then(invalidate),
    markRead: (id) => markBroadcastRead(authFetch, id).then(invalidate),
  }
}
