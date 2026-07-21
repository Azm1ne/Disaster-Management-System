import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, test, vi } from 'vitest'
import App from './App'
import { AuthProvider } from '@/auth/AuthContext'
import i18n from './i18n'

// The map workspace pulls in Leaflet and the world API; neither belongs in a routing test, so
// stand it in for a marker these tests can assert on.
vi.mock('@/world/WorldWorkspace', () => ({
  WorldWorkspace: () => <div>world-workspace</div>,
}))

// Routing tests should not open a real socket or poll the clock; the realtime seam has its own
// tests. Stand both in so the shell renders exactly as it would with the feed quiet.
vi.mock('@/realtime/RealtimeProvider', () => ({
  RealtimeProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useRealtime: () => ({ connected: false, subscribe: () => () => {} }),
  useTopic: () => {},
  refetchIntervalFor: () => false,
}))

vi.mock('@/sim/useSimulationClock', () => ({
  CLOCK_QUERY_KEY: ['simulation', 'clock'],
  useSimulationClock: () => undefined,
}))

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

function seedSession(role: string, nameEn: string) {
  localStorage.setItem('dms.access', 'test-access')
  localStorage.setItem('dms.refresh', 'test-refresh')
  localStorage.setItem(
    'dms.user',
    JSON.stringify({ username: role.toLowerCase(), role, nameEn, nameBn: 'বাংলা নাম' }),
  )
}

beforeEach(async () => {
  localStorage.clear()
  await i18n.changeLanguage('en')
})

test('a signed-out visitor to a protected route is sent to login', async () => {
  renderAt('/coordinator')
  expect(
    await screen.findByRole('heading', { name: 'Sign in to the operation' }),
  ).toBeInTheDocument()
})

test('a signed-in user lands on their own role workspace', () => {
  seedSession('COORDINATOR', 'Rehana Karim')
  renderAt('/coordinator')
  // The role label appears in the shell topbar, and the map workspace is mounted.
  expect(screen.getAllByText('Relief Coordinator').length).toBeGreaterThan(0)
  expect(screen.getByText('world-workspace')).toBeInTheDocument()
})

test('a user is kept out of another role’s route', () => {
  // A coordinator navigating to the donor route is redirected home to the operator shell,
  // not shown the donor (field) workspace.
  seedSession('COORDINATOR', 'Rehana Karim')
  renderAt('/donor')
  // The operator status ribbon (DEMO badge) proves we landed on the coordinator shell. The badge
  // also rides the simulation-control toggle, so more than one is expected here.
  expect(screen.getAllByText('DEMO').length).toBeGreaterThan(0)
})
