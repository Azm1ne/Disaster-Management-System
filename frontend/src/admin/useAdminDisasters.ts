import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/auth/AuthContext'
import { DISASTERS_QUERY_KEY, useDisasters } from '@/world/useDisasters'
import {
  closeDisaster,
  createAffectedArea,
  createCamp,
  createDisaster,
  fetchGeometryHistory,
  updateDisaster,
  type CreateAffectedAreaRequest,
  type CreateCampRequest,
  type CreateDisasterRequest,
  type GeometrySubjectType,
  type UpdateDisasterRequest,
} from '@/admin/api'

/** The disaster roster admin manages — the same shared world-read query every shell surface
 * uses, so a create/edit/close here refreshes everyone's map without a second source of truth. */
export function useAdminDisasterList() {
  return useDisasters()
}

/** Write actions below are plain async closures, not `useMutation` — matches
 * `useTransitionAllocation`, the only write-hook precedent in this codebase. Each invalidates the
 * shared disasters query on success so every reader of it (this page, the world map) refreshes. */

export function useCreateDisaster() {
  const { authFetch } = useAuth()
  const queryClient = useQueryClient()
  return async (request: CreateDisasterRequest) => {
    const disaster = await createDisaster(authFetch, request)
    await queryClient.invalidateQueries({ queryKey: DISASTERS_QUERY_KEY })
    return disaster
  }
}

export function useUpdateDisaster() {
  const { authFetch } = useAuth()
  const queryClient = useQueryClient()
  return async (id: number, request: UpdateDisasterRequest) => {
    const disaster = await updateDisaster(authFetch, id, request)
    await queryClient.invalidateQueries({ queryKey: DISASTERS_QUERY_KEY })
    return disaster
  }
}

export function useCloseDisaster() {
  const { authFetch } = useAuth()
  const queryClient = useQueryClient()
  return async (id: number) => {
    const disaster = await closeDisaster(authFetch, id)
    await queryClient.invalidateQueries({ queryKey: DISASTERS_QUERY_KEY })
    return disaster
  }
}

export function useCreateAffectedArea() {
  const { authFetch } = useAuth()
  const queryClient = useQueryClient()
  return async (disasterId: number, request: CreateAffectedAreaRequest) => {
    const area = await createAffectedArea(authFetch, disasterId, request)
    await queryClient.invalidateQueries({ queryKey: DISASTERS_QUERY_KEY })
    return area
  }
}

export function useCreateCamp() {
  const { authFetch } = useAuth()
  const queryClient = useQueryClient()
  return async (disasterId: number, request: CreateCampRequest) => {
    const camp = await createCamp(authFetch, disasterId, request)
    await queryClient.invalidateQueries({ queryKey: DISASTERS_QUERY_KEY })
    return camp
  }
}

/** A subject's geometry-edit trail, oldest first as the backend already orders it. Not shared
 * with any other reader, so it gets its own small query keyed by subject. */
export function useGeometryHistory(subjectType: GeometrySubjectType, subjectId: number | null) {
  const { authFetch } = useAuth()
  return useQuery({
    queryKey: ['admin', 'geometryHistory', subjectType, subjectId],
    queryFn: () => fetchGeometryHistory(authFetch, subjectType, subjectId as number),
    enabled: subjectId !== null,
  })
}
