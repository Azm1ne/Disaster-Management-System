import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { AdminDisasterWorkspace } from '@/admin/AdminDisasterWorkspace'
import type { CreateAffectedAreaRequest, CreateCampRequest, CreateDisasterRequest, UpdateDisasterRequest } from '@/admin/api'
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
    nameBn: 'যমুনা নদীর বন্যা',
    geometry: FAKE_GEOMETRY,
    affectedAreas: [{ id: 9, nameEn: 'Kurigram Sadar', nameBn: 'কুড়িগ্রাম সদর', geometry: FAKE_GEOMETRY }],
    camps: [],
  },
  {
    id: 2,
    code: 'coastal-cyclone',
    type: 'CYCLONE',
    status: 'CLOSED',
    nameEn: 'Coastal Cyclone',
    nameBn: 'উপকূলীয় ঘূর্ণিঝড়',
    geometry: null,
    affectedAreas: [],
    camps: [],
  },
]

// DisasterDrawMap needs real react-leaflet layout machinery this test environment doesn't
// provide (see DisasterDrawMap.test.tsx's note — no test in this codebase renders a real
// react-leaflet map). It's swapped for a stub exposing its two callbacks as plain buttons, so
// the workspace's own state/wiring is what's under test here, not the map internals.
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

const createDisaster = vi.fn<(request: CreateDisasterRequest) => Promise<unknown>>()
const updateDisaster = vi.fn<(id: number, request: UpdateDisasterRequest) => Promise<unknown>>()
const closeDisaster = vi.fn<(id: number) => Promise<unknown>>()
const createAffectedArea = vi.fn<(disasterId: number, request: CreateAffectedAreaRequest) => Promise<unknown>>()
const createCamp = vi.fn<(disasterId: number, request: CreateCampRequest) => Promise<unknown>>()

vi.mock('@/admin/useAdminDisasters', () => ({
  useAdminDisasterList: () => ({ status: 'ready', disasters }),
  useCreateDisaster: () => createDisaster,
  useUpdateDisaster: () => updateDisaster,
  useCloseDisaster: () => closeDisaster,
  useCreateAffectedArea: () => createAffectedArea,
  useCreateCamp: () => createCamp,
  useGeometryHistory: () => ({ data: [], isPending: false }),
}))

beforeEach(async () => {
  vi.clearAllMocks()
  createDisaster.mockResolvedValue({})
  updateDisaster.mockResolvedValue({})
  closeDisaster.mockResolvedValue({})
  createAffectedArea.mockResolvedValue({})
  createCamp.mockResolvedValue({})
  await i18n.changeLanguage('en')
})

test('renders the disaster roster with name, code, type, and status', () => {
  render(<AdminDisasterWorkspace />)

  expect(screen.getByText('Jamuna River Flood')).toBeInTheDocument()
  expect(screen.getByText('jamuna-flood · Flood')).toBeInTheDocument()
  expect(screen.getByText('Active')).toBeInTheDocument()
  expect(screen.getByText('Coastal Cyclone')).toBeInTheDocument()
  expect(screen.getByText('Closed')).toBeInTheDocument()
})

test('declaring a new disaster submits the drawn geometry and form fields together', async () => {
  const user = userEvent.setup()
  render(<AdminDisasterWorkspace />)

  await user.click(screen.getByText('Declare new disaster'))
  await user.click(screen.getByText('draw-polygon'))
  await user.type(screen.getByLabelText('Code'), 'new-storm')
  await user.selectOptions(screen.getByLabelText('Type'), 'CYCLONE')
  await user.type(screen.getByLabelText('Name (English)'), 'New Storm')
  await user.type(screen.getByLabelText('Name (Bangla)'), 'নতুন ঝড়')
  await user.click(screen.getByText('Declare disaster'))

  expect(createDisaster).toHaveBeenCalledWith({
    code: 'new-storm',
    type: 'CYCLONE',
    nameEn: 'New Storm',
    nameBn: 'নতুন ঝড়',
    geometry: FAKE_GEOMETRY,
  })
})

test('the declare form cannot be submitted before a boundary is drawn', async () => {
  const user = userEvent.setup()
  render(<AdminDisasterWorkspace />)

  await user.click(screen.getByText('Declare new disaster'))
  await user.type(screen.getByLabelText('Code'), 'new-storm')
  await user.type(screen.getByLabelText('Name (English)'), 'New Storm')
  await user.type(screen.getByLabelText('Name (Bangla)'), 'নতুন ঝড়')

  expect(screen.getByText('Declare disaster')).toBeDisabled()
})

test('editing a disaster only sends the fields that actually changed', async () => {
  const user = userEvent.setup()
  render(<AdminDisasterWorkspace />)

  await user.click(screen.getByText('Jamuna River Flood'))
  await user.click(screen.getByText('Redraw boundary'))

  const nameEnInput = screen.getByLabelText('Name (English)')
  await user.clear(nameEnInput)
  await user.type(nameEnInput, 'Jamuna River Flood — Updated')
  // nameBn is left untouched, and the boundary is not redrawn.
  await user.click(screen.getByText('Save boundary'))

  expect(updateDisaster).toHaveBeenCalledWith(1, { nameEn: 'Jamuna River Flood — Updated' })
})

test('redrawing a disaster boundary without changing its name sends only the geometry', async () => {
  const user = userEvent.setup()
  render(<AdminDisasterWorkspace />)

  await user.click(screen.getByText('Jamuna River Flood'))
  await user.click(screen.getByText('Redraw boundary'))
  await user.click(screen.getByText('draw-polygon'))
  await user.click(screen.getByText('Save boundary'))

  expect(updateDisaster).toHaveBeenCalledWith(1, { geometry: FAKE_GEOMETRY })
})

test('closing a disaster requires a second confirming click', async () => {
  const user = userEvent.setup()
  render(<AdminDisasterWorkspace />)

  await user.click(screen.getByText('Jamuna River Flood'))
  await user.click(screen.getByText('Close disaster'))
  expect(closeDisaster).not.toHaveBeenCalled()

  await user.click(screen.getByText('Confirm close'))
  expect(closeDisaster).toHaveBeenCalledWith(1)
})

test('a closed disaster offers no boundary/area/camp/close actions, only overview and history', async () => {
  const user = userEvent.setup()
  render(<AdminDisasterWorkspace />)

  await user.click(screen.getByText('Coastal Cyclone'))

  expect(screen.queryByText('Redraw boundary')).not.toBeInTheDocument()
  expect(screen.queryByText('Add affected area')).not.toBeInTheDocument()
  expect(screen.queryByText('Add camp')).not.toBeInTheDocument()
  expect(screen.queryByText('Close disaster')).not.toBeInTheDocument()
  expect(screen.getByText('Geometry history')).toBeInTheDocument()
})

test('placing a new affected area submits its drawn polygon and names', async () => {
  const user = userEvent.setup()
  render(<AdminDisasterWorkspace />)

  await user.click(screen.getByText('Jamuna River Flood'))
  await user.click(screen.getByText('Add affected area'))
  await user.click(screen.getByText('draw-polygon'))
  await user.type(screen.getByLabelText('Name (English)'), 'New Affected Area')
  await user.type(screen.getByLabelText('Name (Bangla)'), 'নতুন এলাকা')
  await user.click(screen.getByText('Add area'))

  expect(createAffectedArea).toHaveBeenCalledWith(1, {
    nameEn: 'New Affected Area',
    nameBn: 'নতুন এলাকা',
    geometry: FAKE_GEOMETRY,
  })
})

test('placing a new camp submits its clicked point plus the camp form fields', async () => {
  const user = userEvent.setup()
  render(<AdminDisasterWorkspace />)

  await user.click(screen.getByText('Jamuna River Flood'))
  await user.click(screen.getByText('Add camp'))
  await user.click(screen.getByText('place-point'))
  await user.type(screen.getByLabelText('Camp code'), 'jam-new-camp')
  await user.type(screen.getByLabelText('Name (English)'), 'New Camp')
  await user.type(screen.getByLabelText('Name (Bangla)'), 'নতুন ক্যাম্প')
  await user.type(screen.getByLabelText('Capacity'), '500')
  await user.type(screen.getByLabelText('Initial population'), '100')
  await user.click(screen.getByText('Register camp'))

  expect(createCamp).toHaveBeenCalledWith(1, {
    code: 'jam-new-camp',
    nameEn: 'New Camp',
    nameBn: 'নতুন ক্যাম্প',
    lat: 24.2,
    lng: 89.7,
    capacity: 500,
    initialPopulation: 100,
  })
})

test('the geometry-history tab shows the empty state when no history is recorded', async () => {
  const user = userEvent.setup()
  render(<AdminDisasterWorkspace />)

  await user.click(screen.getByText('Jamuna River Flood'))
  await user.click(screen.getByText('Geometry history'))

  expect(screen.getByText('No geometry changes recorded yet.')).toBeInTheDocument()
})
