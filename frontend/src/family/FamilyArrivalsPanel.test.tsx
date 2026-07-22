import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { AuthProvider } from '@/auth/AuthContext'
import { MyCampArrivalsPanel } from '@/family/FamilyArrivalsPanel'
import type { CampArrivalsView } from '@/family/api'
import type { CampDetail } from '@/world/api'
import i18n from '@/i18n'

const camp: CampDetail = {
  id: 7,
  code: 'jam-kurigram-sadar',
  nameEn: 'Kurigram Sadar Govt College Shelter',
  nameBn: 'কুড়িগ্রাম সদর সরকারি কলেজ আশ্রয়কেন্দ্র',
  lat: 25.8,
  lng: 89.6,
  capacity: 1200,
  population: 900,
  status: 'OPEN',
  disaster: { id: 1, nameEn: 'Jamuna River Flood', nameBn: 'যমুনা নদীর বন্যা' },
  resources: [],
}

const myCamp = vi.fn<() => CampDetail | undefined>(() => camp)
vi.mock('@/world/useMyCamp', () => ({ useMyCamp: () => myCamp() }))

const fetchCampArrivals = vi.fn<() => Promise<CampArrivalsView>>()
const confirmGroupArrival = vi.fn()
const setMemberMedicalFlag = vi.fn()

vi.mock('@/family/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/family/api')>()),
  fetchCampArrivals: () => fetchCampArrivals(),
  confirmGroupArrival: (...args: unknown[]) => confirmGroupArrival(...args),
  setMemberMedicalFlag: (...args: unknown[]) => setMemberMedicalFlag(...args),
}))

function signIn() {
  localStorage.setItem('dms.access', 'test-access')
  localStorage.setItem(
    'dms.user',
    JSON.stringify({ username: 'camp_manager', role: 'CAMP_MANAGER', nameEn: 'Anwar Hossain', nameBn: 'আনোয়ার হোসেন' }),
  )
}

function renderPanel() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider>
        <MyCampArrivalsPanel />
      </AuthProvider>
    </QueryClientProvider>,
  )
}

beforeEach(async () => {
  localStorage.clear()
  vi.clearAllMocks()
  myCamp.mockReturnValue(camp)
  signIn()
  await i18n.changeLanguage('en')
})

test('a camp manager sees the arriving/arrived counts and can confirm a group', async () => {
  fetchCampArrivals.mockResolvedValue({
    arrivingCount: 1,
    arrivedCount: 0,
    groups: [
      {
        id: 1,
        groupName: 'Rahman Household',
        memberCount: 2,
        representativeArrived: true,
        managerConfirmedArrived: false,
        status: 'ARRIVING',
        members: [
          { id: 1, nickname: 'Abbu', ageBand: 'ADULT', medicalFlag: false },
          { id: 2, nickname: 'Nodi', ageBand: 'CHILD', medicalFlag: false },
        ],
      },
    ],
  })
  const user = userEvent.setup()
  renderPanel()

  expect(await screen.findByText('Rahman Household')).toBeInTheDocument()
  expect(screen.getByText('1 arriving')).toBeInTheDocument()
  expect(screen.getByText('0 arrived')).toBeInTheDocument()

  await user.click(screen.getByRole('button', { name: 'Confirm arrival' }))
  await waitFor(() => expect(confirmGroupArrival).toHaveBeenCalledWith(expect.anything(), 7, 1))
})

test('a staff member can flag a member for medical attention', async () => {
  fetchCampArrivals.mockResolvedValue({
    arrivingCount: 0,
    arrivedCount: 1,
    groups: [
      {
        id: 1,
        groupName: 'Rahman Household',
        memberCount: 1,
        representativeArrived: true,
        managerConfirmedArrived: true,
        status: 'ARRIVED',
        members: [{ id: 1, nickname: 'Abbu', ageBand: 'ADULT', medicalFlag: false }],
      },
    ],
  })
  const user = userEvent.setup()
  renderPanel()

  await user.click(await screen.findByLabelText('Abbu'))
  await waitFor(() =>
    expect(setMemberMedicalFlag).toHaveBeenCalledWith(expect.anything(), 7, 1, 1, true),
  )
})

test('nothing renders for a manager with no camp', () => {
  myCamp.mockReturnValue(undefined)
  const { container } = renderPanel()
  expect(container).toBeEmptyDOMElement()
})
