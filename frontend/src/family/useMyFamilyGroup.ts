import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/auth/AuthContext'
import {
  confirmMyArrival,
  fetchMyFamilyGroup,
  registerFamilyGroup,
  type FamilyGroupStatus,
  type MemberInput,
} from '@/family/api'

const MY_GROUP_QUERY_KEY = ['family', 'me'] as const

/**
 * The signed-in victim's own registered group, if any — the representative's half of dual-source
 * arrival. No realtime topic: the manager's confirmation is a rare, deliberate act (not a
 * simulation tick), so a refetch on mutation success is enough to stay current.
 */
export function useMyFamilyGroup() {
  const { authFetch } = useAuth()
  const queryClient = useQueryClient()

  const query = useQuery({
    queryKey: MY_GROUP_QUERY_KEY,
    queryFn: () => fetchMyFamilyGroup(authFetch),
  })

  const register = useMutation({
    mutationFn: (request: { campId: number; groupName: string; members: MemberInput[] }) =>
      registerFamilyGroup(authFetch, request),
    onSuccess: (group: FamilyGroupStatus) => queryClient.setQueryData(MY_GROUP_QUERY_KEY, group),
  })

  const confirmArrival = useMutation({
    mutationFn: () => confirmMyArrival(authFetch),
    onSuccess: (group: FamilyGroupStatus) => queryClient.setQueryData(MY_GROUP_QUERY_KEY, group),
  })

  return { query, register, confirmArrival }
}
