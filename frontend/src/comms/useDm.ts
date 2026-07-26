import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/auth/AuthContext'
import { refetchIntervalFor, useRealtime, useTopic } from '@/realtime/RealtimeProvider'
import { fetchDmContacts, fetchDmThread, sendDm } from '@/comms/api'
import { fetchMe } from '@/world/api'

/** Who the signed-in user is permitted to DM — server-derived from the real operational
 * relationships (Coordinator&lt;-&gt;Camp Manager, Camp Manager&lt;-&gt;their Volunteers), never
 * a free-text picker. */
export function useDmContacts() {
  const { authFetch } = useAuth()
  const contacts = useQuery({ queryKey: ['dm-contacts'], queryFn: () => fetchDmContacts(authFetch) })
  return contacts.data
}

/** This user's own id, needed to subscribe to their own `/topic/dm/<userId>` inbox and to tell
 * sent messages apart from received ones. */
export function useMyUserId(): number | undefined {
  const { authFetch } = useAuth()
  const me = useQuery({ queryKey: ['me'], queryFn: () => fetchMe(authFetch) })
  return me.data?.userId
}

/** The 1:1 thread with one other user, live over `/topic/dm/<myUserId>` with polling as
 * fallback — mirrors `useAlertDetail`. A refused relationship simply 403s at fetch time; the UI
 * only offers contacts the server already agreed to. */
export function useDmThread(otherUserId: number | null) {
  const { authFetch } = useAuth()
  const { connected } = useRealtime()
  const queryClient = useQueryClient()
  const myUserId = useMyUserId()
  const key = ['dm-thread', otherUserId] as const

  const thread = useQuery({
    queryKey: key,
    queryFn: () => fetchDmThread(authFetch, otherUserId as number),
    enabled: otherUserId !== null,
    refetchInterval: refetchIntervalFor(connected),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: key })
  useTopic(myUserId === undefined ? null : `/topic/dm/${myUserId}`, invalidate)

  return {
    messages: thread.data,
    send: (body: string) => sendDm(authFetch, otherUserId as number, body).then(invalidate),
  }
}
