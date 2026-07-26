import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/auth/AuthContext'
import { addAllocationNote, fetchAllocationNotes } from '@/comms/api'

/** An allocation's case-note thread (ticket 12, extending the alert case notes from ticket 06)
 * — polled, no dedicated topic, same tradeoff `useAllocations` already made for the queue
 * itself. */
export function useAllocationNotes(allocationId: number | null) {
  const { authFetch } = useAuth()
  const queryClient = useQueryClient()
  const key = ['allocation-notes', allocationId] as const

  const notes = useQuery({
    queryKey: key,
    queryFn: () => fetchAllocationNotes(authFetch, allocationId as number),
    enabled: allocationId !== null,
  })

  return {
    notes: notes.data,
    addNote: (body: string) =>
      addAllocationNote(authFetch, allocationId as number, body).then(() =>
        queryClient.invalidateQueries({ queryKey: key }),
      ),
  }
}
