import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AlertWorkspace } from '@/alerts/AlertWorkspace'
import { AuthProvider } from '@/auth/AuthContext'
import i18n from '@/i18n'

vi.mock('@/alerts/useAlerts', () => ({
  useAlerts: () => [
    {
      id: 1,
      type: 'RESOURCE_SHORTAGE',
      status: 'NEW',
      campId: 1,
      description: 'Water running low',
      raisedByUserId: 2,
      raisedAtTick: 0,
      slaDeadlineTick: 5,
      canAct: true,
      createdAt: '2026-07-22T00:00:00Z',
      updatedAt: '2026-07-22T00:00:00Z',
    },
  ],
}))

vi.mock('@/alerts/useAlertDetail', () => ({
  useAlertDetail: () => ({ detail: undefined, transition: vi.fn(), addNote: vi.fn() }),
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

beforeEach(async () => {
  localStorage.clear()
  vi.clearAllMocks()
  await i18n.changeLanguage('en')
})

describe('AlertWorkspace', () => {
  it('lists an alert by its translated type and status', () => {
    signInAs('COORDINATOR')
    const queryClient = new QueryClient()
    render(
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <AlertWorkspace />
        </AuthProvider>
      </QueryClientProvider>,
    )

    expect(screen.getByText('Resource shortage')).toBeInTheDocument()
    expect(screen.getByText('New')).toBeInTheDocument()
  })
})
