import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from '@/auth/AuthContext'
import { ProtectedRoute } from '@/auth/ProtectedRoute'
import Login from '@/routes/Login'
import { OperatorShell } from '@/shells/OperatorShell'
import { FieldShell } from '@/shells/FieldShell'
import { ROLES, homePathForRole, type RoleConfig } from '@/roles'

function Workspace({ config }: { config: RoleConfig }) {
  return config.shell === 'operator' ? (
    <OperatorShell config={config} />
  ) : (
    <FieldShell config={config} />
  )
}

function RootRedirect() {
  const { user } = useAuth()
  return <Navigate to={user ? homePathForRole(user.role) : '/login'} replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      {ROLES.map((config) => (
        <Route
          key={config.apiRole}
          path={config.path}
          element={
            <ProtectedRoute apiRole={config.apiRole}>
              <Workspace config={config} />
            </ProtectedRoute>
          }
        />
      ))}
      <Route path="*" element={<RootRedirect />} />
    </Routes>
  )
}
