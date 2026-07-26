import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { AuthProvider } from '@/auth/AuthContext'
import { FundsReportWorkspace } from '@/funds/FundsReportWorkspace'
import type { FundsReport } from '@/funds/api'
import type { Disaster } from '@/world/api'
import i18n from '@/i18n'

const disasters: Disaster[] = [
  {
    id: 1,
    code: 'jamuna-flood',
    type: 'FLOOD',
    status: 'ACTIVE',
    nameEn: 'Jamuna River Flood',
    nameBn: 'যমুনা নদীর বন্যা',
    affectedAreas: [],
    camps: [
      {
        id: 7,
        code: 'jam-kurigram-sadar',
        nameEn: 'Kurigram Sadar Govt College Shelter',
        nameBn: 'x',
        lat: 25.8,
        lng: 89.6,
        capacity: 1200,
        population: 900,
        status: 'OPEN',
      },
    ],
  },
]

vi.mock('@/world/useDisasters', () => ({
  useDisasters: () => ({ status: 'ready', disasters }),
}))

const fetchFundsReport = vi.fn<() => Promise<FundsReport>>()
const procure = vi.fn()

vi.mock('@/funds/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/funds/api')>()),
  fetchFundsReport: () => fetchFundsReport(),
  procure: (...args: unknown[]) => procure(...args),
}))

function signIn() {
  localStorage.setItem('dms.access', 'test-access')
  localStorage.setItem(
    'dms.user',
    JSON.stringify({ username: 'coordinator', role: 'COORDINATOR', nameEn: 'Rehana Karim', nameBn: 'x' }),
  )
}

function renderWorkspace() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider>
        <FundsReportWorkspace />
      </AuthProvider>
    </QueryClientProvider>,
  )
}

beforeEach(async () => {
  localStorage.clear()
  vi.clearAllMocks()
  signIn()
  await i18n.changeLanguage('en')
})

test('the report table shows donated, procured, and unaccounted per disaster and totals', async () => {
  fetchFundsReport.mockResolvedValue({
    disasters: [
      { disasterId: 1, nameEn: 'Jamuna River Flood', nameBn: 'x', donated: 1000, procured: 400, unaccounted: 600 },
    ],
    totalDonated: 1000,
    totalProcured: 400,
    totalUnaccounted: 600,
  })
  renderWorkspace()

  expect((await screen.findAllByText('৳1,000')).length).toBeGreaterThan(0)
  expect(screen.getAllByText('৳400').length).toBeGreaterThan(0)
  expect(screen.getAllByText('৳600').length).toBeGreaterThan(0)
})

test('a coordinator can submit a procurement against a chosen camp and resource', async () => {
  fetchFundsReport.mockResolvedValue({ disasters: [], totalDonated: 0, totalProcured: 0, totalUnaccounted: 0 })
  procure.mockResolvedValue({})
  const user = userEvent.setup()
  renderWorkspace()

  await screen.findByText('Procure resources')
  await user.selectOptions(screen.getByLabelText('Disaster'), '1')
  await user.selectOptions(screen.getByLabelText('Camp'), '7')
  await user.selectOptions(screen.getByLabelText('Resource'), 'FOOD')
  await user.type(screen.getByPlaceholderText('৳'), '150')
  await user.click(screen.getByRole('button', { name: 'Procure' }))

  await waitFor(() => expect(procure).toHaveBeenCalledWith(expect.anything(), 1, 7, 'FOOD', 150))
})
