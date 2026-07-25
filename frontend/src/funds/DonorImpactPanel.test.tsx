import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { AuthProvider } from '@/auth/AuthContext'
import { DonorImpactPanel } from '@/funds/DonorImpactPanel'
import type { DonationView, DonorImpactView } from '@/funds/api'
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
    camps: [],
  },
]

vi.mock('@/world/useDisasters', () => ({
  useDisasters: () => ({ status: 'ready', disasters }),
}))

const fetchMyDonations = vi.fn<() => Promise<DonationView[]>>()
const fetchMyImpact = vi.fn<() => Promise<DonorImpactView>>()
const donate = vi.fn()

vi.mock('@/funds/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/funds/api')>()),
  fetchMyDonations: () => fetchMyDonations(),
  fetchMyImpact: () => fetchMyImpact(),
  donate: (...args: unknown[]) => donate(...args),
}))

function signIn() {
  localStorage.setItem('dms.access', 'test-access')
  localStorage.setItem(
    'dms.user',
    JSON.stringify({ username: 'donor', role: 'DONOR', nameEn: 'Farhana Ahmed', nameBn: 'ফারহানা আহমেদ' }),
  )
}

function renderPanel() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider>
        <DonorImpactPanel />
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

test('a donor with no history sees only the donation form', async () => {
  fetchMyDonations.mockResolvedValue([])
  fetchMyImpact.mockResolvedValue({ disasters: [], totalDonatedByMe: 0 })
  renderPanel()

  expect(await screen.findByText('Make a donation')).toBeInTheDocument()
  expect(screen.queryByText('Your impact')).not.toBeInTheDocument()
})

test('a donor can submit a donation to a disaster', async () => {
  fetchMyDonations.mockResolvedValue([])
  fetchMyImpact.mockResolvedValue({ disasters: [], totalDonatedByMe: 0 })
  donate.mockResolvedValue({ id: 1, disasterId: 1, disasterNameEn: 'x', disasterNameBn: 'x', amount: 500, createdAt: '' })
  const user = userEvent.setup()
  renderPanel()

  await screen.findByText('Make a donation')
  await user.selectOptions(screen.getByLabelText('Disaster'), '1')
  await user.type(screen.getByPlaceholderText('0'), '500')
  await user.click(screen.getByRole('button', { name: 'Donate' }))

  await waitFor(() => expect(donate).toHaveBeenCalledWith(expect.anything(), 1, 500))
})

test('a donor with history sees the aggregated Donation → Camp chain, camp-only detail', async () => {
  fetchMyDonations.mockResolvedValue([
    { id: 1, disasterId: 1, disasterNameEn: 'Jamuna River Flood', disasterNameBn: 'x', amount: 500, createdAt: '' },
  ])
  fetchMyImpact.mockResolvedValue({
    totalDonatedByMe: 500,
    disasters: [
      {
        disasterId: 1,
        nameEn: 'Jamuna River Flood',
        nameBn: 'যমুনা নদীর বন্যা',
        donatedByMe: 500,
        camps: [
          {
            campId: 7,
            campNameEn: 'Kurigram Sadar Govt College Shelter',
            campNameBn: 'x',
            resourceType: 'WATER',
            amountProcured: 200,
            quantityProcured: 40,
          },
        ],
      },
    ],
  })
  renderPanel()

  expect(await screen.findByText('Your impact')).toBeInTheDocument()
  expect(screen.getByText('Kurigram Sadar Govt College Shelter')).toBeInTheDocument()
  expect(screen.getByText('Water')).toBeInTheDocument()
  // No victim/family vocabulary anywhere in the rendered chain.
  expect(screen.queryByText(/victim|family|member/i)).not.toBeInTheDocument()
})
