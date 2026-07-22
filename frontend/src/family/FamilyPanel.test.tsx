import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { AuthProvider } from '@/auth/AuthContext'
import { FamilyPanel } from '@/family/FamilyPanel'
import type { FamilyGroupStatus } from '@/family/api'
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
        nameBn: 'কুড়িগ্রাম সদর সরকারি কলেজ আশ্রয়কেন্দ্র',
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

const fetchMyFamilyGroup = vi.fn<() => Promise<FamilyGroupStatus | null>>()
const registerFamilyGroup = vi.fn()
const confirmMyArrival = vi.fn()

vi.mock('@/family/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/family/api')>()),
  fetchMyFamilyGroup: () => fetchMyFamilyGroup(),
  registerFamilyGroup: (...args: unknown[]) => registerFamilyGroup(...args),
  confirmMyArrival: () => confirmMyArrival(),
}))

function signIn() {
  localStorage.setItem('dms.access', 'test-access')
  localStorage.setItem(
    'dms.user',
    JSON.stringify({ username: 'victim', role: 'VICTIM', nameEn: 'Jesmin Begum', nameBn: 'জেসমিন বেগম' }),
  )
}

function renderPanel() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider>
        <FamilyPanel />
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

test('an unregistered victim sees the registration form and can submit a household', async () => {
  fetchMyFamilyGroup.mockResolvedValue(null)
  const user = userEvent.setup()
  renderPanel()

  expect(await screen.findByText('Register your family')).toBeInTheDocument()

  await user.type(screen.getByLabelText('Family or group name'), 'Rahman Household')
  await user.selectOptions(screen.getByLabelText('Destination camp'), '7')
  await user.type(screen.getByPlaceholderText('Nickname'), 'Abbu')
  await user.click(screen.getByRole('button', { name: 'Register' }))

  await waitFor(() =>
    expect(registerFamilyGroup).toHaveBeenCalledWith(expect.anything(), {
      campId: 7,
      groupName: 'Rahman Household',
      members: [{ nickname: 'Abbu', ageBand: 'ADULT' }],
    }),
  )
})

test('a registered victim sees their group status and dual-source arrival stamps', async () => {
  fetchMyFamilyGroup.mockResolvedValue({
    id: 1,
    groupName: 'Rahman Household',
    campId: 7,
    campNameEn: 'Kurigram Sadar Govt College Shelter',
    campNameBn: 'কুড়িগ্রাম সদর সরকারি কলেজ আশ্রয়কেন্দ্র',
    memberCount: 2,
    representativeArrived: false,
    managerConfirmedArrived: false,
    status: 'REGISTERED',
    members: [
      { id: 1, nickname: 'Abbu', ageBand: 'ADULT', medicalFlag: false },
      { id: 2, nickname: 'Nodi', ageBand: 'CHILD', medicalFlag: false },
    ],
  })
  const user = userEvent.setup()
  renderPanel()

  expect(await screen.findByText('Rahman Household')).toBeInTheDocument()
  expect(screen.getByText('2 members')).toBeInTheDocument()
  expect(screen.getByText('Not yet travelled')).toBeInTheDocument()

  await user.click(screen.getByRole('button', { name: "I've arrived at the camp" }))
  await waitFor(() => expect(confirmMyArrival).toHaveBeenCalled())
})
