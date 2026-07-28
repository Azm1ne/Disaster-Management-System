import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { AuthProvider } from '@/auth/AuthContext'
import { CoordinatorProposeWorkspace } from '@/proposals/CoordinatorProposeWorkspace'
import type { Disaster, GeoJsonPolygon } from '@/world/api'
import i18n from '@/i18n'

const FAKE_GEOMETRY: GeoJsonPolygon = {
  type: 'Polygon',
  coordinates: [
    [
      [89.5, 24.0],
      [89.5, 24.5],
      [90.0, 24.5],
      [89.5, 24.0],
    ],
  ],
}

const disasters: Disaster[] = [
  {
    id: 1,
    code: 'jamuna-flood',
    type: 'FLOOD',
    status: 'ACTIVE',
    nameEn: 'Jamuna River Flood',
    nameBn: 'x',
    geometry: null,
    affectedAreas: [],
    camps: [],
  },
]

// Real @/proposals/api and @/proposals/useProposals run unmocked here — this test exercises the
// actual geometry-as-string wire detail (`serializePayload` in api.ts), not a stand-in for it.
// Only the network boundary (fetch) and the map (needs real react-leaflet layout this test
// environment doesn't provide) are stubbed, matching AdminDisasterWorkspace.test.tsx's approach.
vi.mock('@/world/useDisasters', () => ({
  useDisasters: () => ({ status: 'ready', disasters }),
}))

vi.mock('@/world/DisasterDrawMap', () => ({
  DisasterDrawMap: ({
    onPolygonDrawn,
    onPointPlaced,
  }: {
    onPolygonDrawn: (g: GeoJsonPolygon) => void
    onPointPlaced: (lat: number, lng: number) => void
  }) => (
    <div>
      <button type="button" onClick={() => onPolygonDrawn(FAKE_GEOMETRY)}>
        draw-polygon
      </button>
      <button type="button" onClick={() => onPointPlaced(24.2, 89.7)}>
        place-point
      </button>
    </div>
  ),
}))

const fetchMock = vi.fn<typeof fetch>()

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
        <CoordinatorProposeWorkspace />
      </AuthProvider>
    </QueryClientProvider>,
  )
}

function bodyOfLastCall(): Record<string, unknown> {
  const call = fetchMock.mock.calls.at(-1)
  const init = call?.[1] as RequestInit
  return JSON.parse(init.body as string) as Record<string, unknown>
}

beforeEach(async () => {
  localStorage.clear()
  vi.clearAllMocks()
  signIn()
  await i18n.changeLanguage('en')
  fetchMock.mockResolvedValue(new Response(JSON.stringify({ id: 99 }), { status: 200 }))
  vi.stubGlobal('fetch', fetchMock)
})

test('a DISASTER_CREATE proposal serializes its drawn geometry to a JSON string, like the admin path', async () => {
  const user = userEvent.setup()
  renderWorkspace()

  // DISASTER_CREATE is the default selected type.
  await user.click(screen.getByText('draw-polygon'))
  await user.type(screen.getByLabelText('Code'), 'new-storm')
  await user.type(screen.getByLabelText('Name (English)'), 'New Storm')
  await user.type(screen.getByLabelText('Name (Bangla)'), 'x')
  await user.click(screen.getByText('Submit proposal'))

  await waitFor(() => expect(fetchMock).toHaveBeenCalled())
  const body = bodyOfLastCall()
  expect(body.proposalType).toBe('DISASTER_CREATE')
  expect(body.targetDisasterId).toBeNull()
  const payload = body.payload as Record<string, unknown>
  expect(payload.code).toBe('new-storm')
  expect(payload.type).toBe('FLOOD')
  expect(payload.geometry).toBe(JSON.stringify(FAKE_GEOMETRY))
})

test('a CAMP_CREATE proposal targets the chosen disaster and carries the placed point plus form fields', async () => {
  const user = userEvent.setup()
  renderWorkspace()

  await user.click(screen.getByText('New camp'))
  await user.selectOptions(screen.getByLabelText('Disaster'), '1')
  await user.click(screen.getByText('place-point'))
  await user.type(screen.getByLabelText('Camp code'), 'jam-new-camp')
  await user.type(screen.getByLabelText('Name (English)'), 'New Camp')
  await user.type(screen.getByLabelText('Name (Bangla)'), 'x')
  await user.type(screen.getByLabelText('Capacity'), '500')
  await user.type(screen.getByLabelText('Initial population'), '100')
  await user.click(screen.getByText('Submit proposal'))

  await waitFor(() => expect(fetchMock).toHaveBeenCalled())
  const body = bodyOfLastCall()
  expect(body.proposalType).toBe('CAMP_CREATE')
  expect(body.targetDisasterId).toBe(1)
  expect(body.payload).toEqual({
    code: 'jam-new-camp',
    nameEn: 'New Camp',
    nameBn: 'x',
    lat: 24.2,
    lng: 89.7,
    capacity: 500,
    initialPopulation: 100,
  })
})
