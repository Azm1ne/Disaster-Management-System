import { render, screen } from '@testing-library/react'
import { beforeEach, expect, test, vi } from 'vitest'
import { MyCampPanel } from '@/world/MyCampPanel'
import type { CampDetail } from '@/world/api'
import i18n from '@/i18n'

const camp: CampDetail = {
  id: 1,
  code: 'jam-kurigram-sadar',
  nameEn: 'Kurigram Sadar Govt College Shelter',
  nameBn: 'কুড়িগ্রাম সদর সরকারি কলেজ আশ্রয়কেন্দ্র',
  lat: 25.806,
  lng: 89.636,
  capacity: 1200,
  population: 1330,
  status: 'OPEN',
  disaster: { id: 1, nameEn: 'Jamuna River Flood', nameBn: 'যমুনা নদীর বন্যা' },
  // Field names mirror the API exactly (`type`, not `resourceType`) — a fixture that drifts from
  // the real payload hides bugs rather than catching them.
  resources: [
    { type: 'FOOD', quantity: 2160, unit: 'meal packs' },
    { type: 'WATER', quantity: 2394.5, unit: 'liters/day' },
    { type: 'MEDICAL', quantity: 1080, unit: 'aid kits' },
  ],
}

const myCamp = vi.fn<() => CampDetail | undefined>(() => camp)
vi.mock('@/world/useMyCamp', () => ({ useMyCamp: () => myCamp() }))

beforeEach(async () => {
  vi.clearAllMocks()
  myCamp.mockReturnValue(camp)
  await i18n.changeLanguage('en')
})

test('a manager sees their own camp with its live occupancy and stock', () => {
  render(<MyCampPanel />)

  expect(screen.getByText('Kurigram Sadar Govt College Shelter')).toBeInTheDocument()
  expect(screen.getByText('1,330 / 1,200')).toBeInTheDocument()
  expect(screen.getByText('2,395')).toBeInTheDocument()
  // Every resource type is labelled from its code, so a shape mismatch with the API shows up here.
  expect(screen.getByText('Water')).toBeInTheDocument()
  expect(screen.getByText('Food')).toBeInTheDocument()
  expect(screen.getByText('Medical')).toBeInTheDocument()
})

test('a camp past its capacity says so, because that is what needs acting on', () => {
  render(<MyCampPanel />)

  expect(screen.getByText('over capacity')).toBeInTheDocument()
})

test('nothing is shown for a user who manages no camp', () => {
  myCamp.mockReturnValue(undefined)

  const { container } = render(<MyCampPanel />)

  expect(container).toBeEmptyDOMElement()
})
