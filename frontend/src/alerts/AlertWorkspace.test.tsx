import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AlertWorkspace } from '@/alerts/AlertWorkspace'
import { AuthProvider } from '@/auth/AuthContext'
import i18n from '@/i18n'

const forecastAlert = {
  id: 1,
  type: 'RESOURCE_SHORTAGE',
  status: 'NEW',
  campId: 1,
  resourceType: 'WATER',
  description: '{"resourceType":"WATER","ticksRemaining":4,"confidence":"LOW"}',
  raisedByUserId: null,
  raisedAtTick: 10,
  slaDeadlineTick: 15,
  canAct: true,
  createdAt: '2024-07-15T06:00:00Z',
  updatedAt: '2024-07-15T06:00:00Z',
}

const useAlerts = vi.fn()
const useAlertDetail = vi.fn()

vi.mock('@/alerts/useAlerts', () => ({
  useAlerts: () => useAlerts(),
}))

vi.mock('@/alerts/useAlertDetail', () => ({
  useAlertDetail: () => useAlertDetail(),
}))

// This codebase's actual convention for a role-bearing test (see SimulationControlPanel.test.tsx):
// seed localStorage and render the real AuthProvider, rather than mocking AuthContext directly.
function signInAs(role: string) {
  localStorage.setItem('dms.access', 'test-access')
  localStorage.setItem(
    'dms.user',
    JSON.stringify({ username: role.toLowerCase(), role, nameEn: 'Test', nameBn: 'পরীক্ষা' }),
  )
}

function renderWorkspace() {
  const queryClient = new QueryClient()
  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <AlertWorkspace />
      </AuthProvider>
    </QueryClientProvider>,
  )
}

beforeEach(async () => {
  localStorage.clear()
  vi.clearAllMocks()
  useAlerts.mockReturnValue([
    {
      id: 1,
      type: 'RESOURCE_SHORTAGE',
      status: 'NEW',
      campId: 1,
      resourceType: null,
      description: 'Water running low',
      raisedByUserId: 2,
      raisedAtTick: 0,
      slaDeadlineTick: 5,
      canAct: true,
      createdAt: '2026-07-22T00:00:00Z',
      updatedAt: '2026-07-22T00:00:00Z',
    },
  ])
  useAlertDetail.mockReturnValue({ detail: undefined, transition: vi.fn(), addNote: vi.fn() })
  await i18n.changeLanguage('en')
})

describe('AlertWorkspace', () => {
  it('lists an alert by its translated type and status', () => {
    signInAs('COORDINATOR')
    renderWorkspace()

    expect(screen.getByText('Resource shortage')).toBeInTheDocument()
    expect(screen.getByText('New')).toBeInTheDocument()
  })

  it('renders a forecast-originated alert through the bilingual template, not raw JSON', async () => {
    signInAs('COORDINATOR')
    useAlertDetail.mockReturnValue({
      detail: { summary: forecastAlert, transitions: [], notes: [] },
      transition: vi.fn(),
      addNote: vi.fn(),
    })
    renderWorkspace()

    await waitFor(() => expect(screen.getByText(/Projected Water exhaustion/i)).toBeInTheDocument())
    expect(screen.queryByText(/"resourceType"/)).not.toBeInTheDocument()
  })

  it('renders the same forecast alert in Bengali when the language is switched', async () => {
    signInAs('COORDINATOR')
    useAlertDetail.mockReturnValue({
      detail: { summary: forecastAlert, transitions: [], notes: [] },
      transition: vi.fn(),
      addNote: vi.fn(),
    })
    await i18n.changeLanguage('bn')
    renderWorkspace()

    await waitFor(() => expect(screen.getByText(/নিঃশেষ হওয়ার আশঙ্কা/)).toBeInTheDocument())
    expect(screen.queryByText(/"resourceType"/)).not.toBeInTheDocument()
  })
})
