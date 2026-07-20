import { useEffect, useState } from 'react'
import { useAuth } from '@/auth/AuthContext'
import { fetchDisasters, type Disaster } from '@/world/api'

type State =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'ready'; disasters: Disaster[] }

/** Loads the authenticated world once. Later slices swap this for a live (STOMP) subscription. */
export function useDisasters(): State {
  const { authFetch } = useAuth()
  const [state, setState] = useState<State>({ status: 'loading' })

  useEffect(() => {
    let active = true
    fetchDisasters(authFetch)
      .then((disasters) => active && setState({ status: 'ready', disasters }))
      .catch(() => active && setState({ status: 'error' }))
    return () => {
      active = false
    }
  }, [authFetch])

  return state
}
