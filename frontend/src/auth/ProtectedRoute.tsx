import { type ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '@/auth/AuthContext'
import { homePathForRole } from '@/roles'

/**
 * Client-side route guard. This is a convenience only — the real access-control boundary is
 * the server, which authorizes every request by role. Here we simply keep a signed-out user
 * on the login page and keep a signed-in user on the route that belongs to their role.
 */
export function ProtectedRoute({ apiRole, children }: { apiRole: string; children: ReactNode }) {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  if (user.role !== apiRole) return <Navigate to={homePathForRole(user.role)} replace />
  return <>{children}</>
}
