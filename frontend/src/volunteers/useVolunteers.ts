import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/auth/AuthContext'
import { refetchIntervalFor, useRealtime } from '@/realtime/RealtimeProvider'
import {
  acceptShift,
  assignVolunteer,
  fetchCandidates,
  fetchMyAssignments,
  fetchRoute,
  fetchSkillGap,
  fetchVolunteerTasks,
  type VolunteerCandidate,
  type VolunteerTaskSummary,
} from '@/volunteers/api'

/** Every task the signed-in user is entitled to see — the coordinator's full queue, or a
 * volunteer's open-shifts board. Polled on the same cadence as the realtime fallback; there is no
 * dedicated volunteer STOMP topic, same tradeoff ticket 07/08 made for forecasts/allocations. */
export function useVolunteerTasks(): VolunteerTaskSummary[] | undefined {
  const { authFetch } = useAuth()
  const { connected } = useRealtime()
  const query = useQuery({
    queryKey: ['volunteerTasks'],
    queryFn: () => fetchVolunteerTasks(authFetch),
    refetchInterval: refetchIntervalFor(connected),
  })
  return query.data
}

/** A volunteer's own accepted shifts and push-assignments, in one place. */
export function useMyAssignments(): VolunteerTaskSummary[] | undefined {
  const { authFetch } = useAuth()
  const { connected } = useRealtime()
  const query = useQuery({
    queryKey: ['myVolunteerAssignments'],
    queryFn: () => fetchMyAssignments(authFetch),
    refetchInterval: refetchIntervalFor(connected),
  })
  return query.data
}

/** The skill-coverage gap panel's data — Coordinator/Admin only. */
export function useSkillGap() {
  const { authFetch } = useAuth()
  const { connected } = useRealtime()
  const query = useQuery({
    queryKey: ['volunteerSkillGap'],
    queryFn: () => fetchSkillGap(authFetch),
    refetchInterval: refetchIntervalFor(connected),
  })
  return query.data
}

/** Ranked candidates for one task, fetched only once a coordinator expands it. */
export function useCandidates(taskId: number | null): VolunteerCandidate[] | undefined {
  const { authFetch } = useAuth()
  const query = useQuery({
    queryKey: ['volunteerCandidates', taskId],
    queryFn: () => fetchCandidates(authFetch, taskId as number),
    enabled: taskId !== null,
  })
  return query.data
}

function useInvalidateVolunteerQueries() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['volunteerTasks'] }),
      queryClient.invalidateQueries({ queryKey: ['myVolunteerAssignments'] }),
      queryClient.invalidateQueries({ queryKey: ['volunteerSkillGap'] }),
      queryClient.invalidateQueries({ queryKey: ['volunteerCandidates'] }),
    ])
}

export function useAssignVolunteer() {
  const { authFetch } = useAuth()
  const invalidate = useInvalidateVolunteerQueries()
  return async (taskId: number, volunteerId: number, note?: string) => {
    await assignVolunteer(authFetch, taskId, volunteerId, note)
    await invalidate()
  }
}

export function useAcceptShift() {
  const { authFetch } = useAuth()
  const invalidate = useInvalidateVolunteerQueries()
  return async (taskId: number) => {
    await acceptShift(authFetch, taskId)
    await invalidate()
  }
}

export function useRouteFetcher() {
  const { authFetch } = useAuth()
  return (taskId: number) => fetchRoute(authFetch, taskId)
}
