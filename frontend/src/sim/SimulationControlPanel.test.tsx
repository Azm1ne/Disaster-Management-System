import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import { AuthProvider } from '@/auth/AuthContext'
import { SimulationControlPanel } from '@/sim/SimulationControlPanel'
import type { SimulationClock } from '@/sim/api'
import i18n from '@/i18n'

const clock: SimulationClock = {
  tick: 24,
  simTime: '2024-07-15T18:00:00Z',
  phase: 'NEW_CAMP',
  running: true,
  speed: 1,
  scenarioLength: 60,
}

vi.mock('@/sim/useSimulationClock', () => ({
  CLOCK_QUERY_KEY: ['simulation', 'clock'],
  useSimulationClock: () => clock,
}))

const pauseSimulation = vi.fn().mockResolvedValue({ ...clock, running: false })
const setSimulationSpeed = vi.fn().mockResolvedValue({ ...clock, speed: 4 })
const resetSimulation = vi.fn().mockResolvedValue({ ...clock, tick: 0 })

vi.mock('@/sim/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/sim/api')>()),
  pauseSimulation: (...args: unknown[]) => pauseSimulation(...args),
  resumeSimulation: vi.fn(),
  setSimulationSpeed: (...args: unknown[]) => setSimulationSpeed(...args),
  resetSimulation: (...args: unknown[]) => resetSimulation(...args),
}))

function signInAs(role: string) {
  localStorage.setItem('dms.access', 'test-access')
  localStorage.setItem(
    'dms.user',
    JSON.stringify({ username: role.toLowerCase(), role, nameEn: 'Test', nameBn: 'পরীক্ষা' }),
  )
}

function renderPanel() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider>
        <SimulationControlPanel onClose={() => {}} />
      </AuthProvider>
    </QueryClientProvider>,
  )
}

beforeEach(async () => {
  localStorage.clear()
  vi.clearAllMocks()
  await i18n.changeLanguage('en')
})

describe('the DEMO simulation control', () => {
  test('is badged DEMO and marks the phase the story is in', () => {
    signInAs('COORDINATOR')
    renderPanel()

    expect(screen.getByText('DEMO')).toBeInTheDocument()
    expect(screen.getByRole('listitem', { current: 'step' })).toHaveTextContent(
      'Overflow camp opens',
    )
    expect(screen.getByText('Tick 24 of 60')).toBeInTheDocument()
  })

  test('a coordinator can pause a running simulation', async () => {
    signInAs('COORDINATOR')
    renderPanel()

    await userEvent.click(screen.getByRole('button', { name: 'Pause' }))

    expect(pauseSimulation).toHaveBeenCalledTimes(1)
  })

  test('a coordinator can change speed, and the current speed is marked', async () => {
    signInAs('COORDINATOR')
    renderPanel()

    expect(screen.getByRole('button', { name: '1×' })).toHaveAttribute('aria-pressed', 'true')
    await userEvent.click(screen.getByRole('button', { name: '4×' }))

    expect(setSimulationSpeed).toHaveBeenCalledWith(expect.anything(), 4)
  })

  test('a camp manager cannot drive the simulation and is told why', () => {
    signInAs('CAMP_MANAGER')
    renderPanel()

    expect(screen.getByRole('button', { name: 'Pause' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Reset to start' })).toBeDisabled()
    expect(
      screen.getByText('Only a Coordinator or Admin can drive the simulation.'),
    ).toBeInTheDocument()
  })
})
