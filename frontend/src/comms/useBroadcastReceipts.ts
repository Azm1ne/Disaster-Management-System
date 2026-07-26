import { useQuery } from '@tanstack/react-query'
import { useAuth } from '@/auth/AuthContext'
import { fetchBroadcastReceipts } from '@/comms/api'

/** Who has read a broadcast — fetched only while its receipts panel is open (`enabled`), since
 * this is sender-only detail, not something every list row needs on every render. */
export function useBroadcastReceipts(broadcastId: number | null) {
  const { authFetch } = useAuth()
  const receipts = useQuery({
    queryKey: ['broadcast-receipts', broadcastId],
    queryFn: () => fetchBroadcastReceipts(authFetch, broadcastId as number),
    enabled: broadcastId !== null,
  })
  return receipts.data
}
