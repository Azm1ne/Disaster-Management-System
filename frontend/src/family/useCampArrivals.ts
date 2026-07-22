import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/auth/AuthContext'
import { confirmGroupArrival, fetchCampArrivals, setMemberMedicalFlag } from '@/family/api'

/** A camp manager's own arrivals queue — the staff half of dual-source arrival, for one camp. */
export function useCampArrivals(campId: number | undefined) {
  const { authFetch } = useAuth()
  const queryClient = useQueryClient()
  const queryKey = ['family', 'campArrivals', campId] as const

  const query = useQuery({
    queryKey,
    queryFn: () => fetchCampArrivals(authFetch, campId as number),
    enabled: campId !== undefined,
  })

  const confirmArrival = useMutation({
    mutationFn: (groupId: number) => confirmGroupArrival(authFetch, campId as number, groupId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey }),
  })

  const setMedicalFlag = useMutation({
    mutationFn: ({ groupId, memberId, value }: { groupId: number; memberId: number; value: boolean }) =>
      setMemberMedicalFlag(authFetch, campId as number, groupId, memberId, value),
    onSuccess: () => queryClient.invalidateQueries({ queryKey }),
  })

  return { query, confirmArrival, setMedicalFlag }
}
