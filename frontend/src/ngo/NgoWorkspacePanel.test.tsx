import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { beforeEach, expect, test, vi } from 'vitest'
import { AuthProvider } from '@/auth/AuthContext'
import { NgoWorkspacePanel } from '@/ngo/NgoWorkspacePanel'
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
    geometry: null,
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

let disastersMock: Disaster[] = disasters

vi.mock('@/world/useDisasters', () => ({
  useDisasters: () => ({ status: 'ready', disasters: disastersMock }),
}))

const fetchFundsReport = vi.fn<() => Promise<FundsReport>>()

vi.mock('@/funds/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/funds/api')>()),
  fetchFundsReport: () => fetchFundsReport(),
}))

function signIn() {
  localStorage.setItem('dms.access', 'test-access')
  localStorage.setItem(
    'dms.user',
    JSON.stringify({ username: 'ngo', role: 'NGO', nameEn: 'BRAC Relief Desk', nameBn: 'ব্র্যাক ত্রাণ ডেস্ক' }),
  )
}

function renderPanel() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider>
        <NgoWorkspacePanel />
      </AuthProvider>
    </QueryClientProvider>,
  )
}

beforeEach(async () => {
  localStorage.clear()
  vi.clearAllMocks()
  signIn()
  disastersMock = disasters
  await i18n.changeLanguage('en')
})

test('NGO sees the engagement section and the unaccounted-funds table for a populated report', async () => {
  fetchFundsReport.mockResolvedValue({
    disasters: [
      { disasterId: 1, nameEn: 'Jamuna River Flood', nameBn: 'x', donated: 1000, procured: 400, unaccounted: 600 },
    ],
    totalDonated: 1000,
    totalProcured: 400,
    totalUnaccounted: 600,
  })
  renderPanel()

  expect(await screen.findByText('Your disaster engagement')).toBeInTheDocument()
  expect(screen.getByText('Jamuna River Flood')).toBeInTheDocument()
  expect(screen.getByText('1 camps')).toBeInTheDocument()
  expect(screen.getByText('Unaccounted funds')).toBeInTheDocument()
  expect((await screen.findAllByText('৳1,000')).length).toBeGreaterThan(0)
  expect(screen.getAllByText('৳400').length).toBeGreaterThan(0)
  expect(screen.getAllByText('৳600').length).toBeGreaterThan(0)
})

test('NGO never sees the procurement form — read-only scope by design', async () => {
  fetchFundsReport.mockResolvedValue({ disasters: [], totalDonated: 0, totalProcured: 0, totalUnaccounted: 0 })
  renderPanel()

  await screen.findByText('Your disaster engagement')
  // The Coordinator/Admin "Procure resources" affordance lives in FundsReportWorkspace —
  // the NGO panel must never surface it, so the procurement label is nowhere here.
  expect(screen.queryByText('Procure resources')).not.toBeInTheDocument()
  // Donor-only affordance is also absent from the NGO view.
  expect(screen.queryByText('Make a donation')).not.toBeInTheDocument()
})

test('NGO sees the empty state when there are no disasters', async () => {
  disastersMock = []
  fetchFundsReport.mockResolvedValue({ disasters: [], totalDonated: 0, totalProcured: 0, totalUnaccounted: 0 })
  renderPanel()

  expect(await screen.findByText('No disasters are active right now.')).toBeInTheDocument()
  // The section title still renders so the NGO understands the role's purpose.
  expect(screen.getByText('Your disaster engagement')).toBeInTheDocument()
})
