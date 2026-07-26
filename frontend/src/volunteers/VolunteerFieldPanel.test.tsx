import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, test, vi } from 'vitest'
import { VolunteerFieldPanel } from '@/volunteers/VolunteerFieldPanel'
import type { RouteView, VolunteerTaskSummary } from '@/volunteers/api'
import i18n from '@/i18n'

const openTask: VolunteerTaskSummary = {
  id: 1,
  alertId: 9,
  campId: 2,
  requiredSkill: 'LOGISTICS',
  description: 'Distribution/logistics volunteers needed',
  status: 'OPEN',
  assignedVolunteerId: null,
  assignedVolunteerNameEn: null,
  assignedVolunteerNameBn: null,
  assignmentMethod: null,
  urgencyScore: 0.5,
  generatedAtTick: 4,
  assignedAtTick: null,
  canAssign: false,
  canAccept: true,
  createdAt: '2026-07-24T00:00:00Z',
  updatedAt: '2026-07-24T00:00:00Z',
}

const myTask: VolunteerTaskSummary = {
  ...openTask,
  id: 2,
  requiredSkill: 'MEDICAL',
  description: 'Medical support volunteers needed',
  status: 'ASSIGNED',
  assignedVolunteerId: 1,
  assignmentMethod: 'PUSH',
  canAccept: false,
}

const route: RouteView = {
  points: [
    [25.8, 89.63],
    [25.81, 89.64],
  ],
  distanceMeters: 1500,
  durationSeconds: 300,
  source: 'STRAIGHT_LINE',
}

const accept = vi.fn()
const fetchRoute = vi.fn(async () => route)
const openShiftsMock = vi.fn<() => VolunteerTaskSummary[] | undefined>(() => [openTask])
const myAssignmentsMock = vi.fn<() => VolunteerTaskSummary[] | undefined>(() => [myTask])

vi.mock('@/volunteers/useVolunteers', () => ({
  useVolunteerTasks: () => openShiftsMock(),
  useMyAssignments: () => myAssignmentsMock(),
  useAcceptShift: () => accept,
  useRouteFetcher: () => fetchRoute,
}))

beforeEach(async () => {
  vi.clearAllMocks()
  openShiftsMock.mockReturnValue([openTask])
  myAssignmentsMock.mockReturnValue([myTask])
  fetchRoute.mockResolvedValue(route)
  await i18n.changeLanguage('en')
})

test('an open shift can be accepted with a single button', () => {
  render(<VolunteerFieldPanel />)

  fireEvent.click(screen.getByText('Accept shift'))
  expect(accept).toHaveBeenCalledWith(1)
})

test('a my-shift card fetches and shows the route on demand', async () => {
  render(<VolunteerFieldPanel />)

  fireEvent.click(screen.getByText('View route'))

  await waitFor(() => expect(fetchRoute).toHaveBeenCalledWith(2))
  expect(await screen.findByText('Straight-line estimate')).toBeInTheDocument()
  expect(screen.getByText('1.5 km · about 5 min')).toBeInTheDocument()
})

test('shows both empty states when nothing is open or assigned', () => {
  openShiftsMock.mockReturnValue([])
  myAssignmentsMock.mockReturnValue([])
  render(<VolunteerFieldPanel />)

  expect(screen.getByText('No open shifts right now.')).toBeInTheDocument()
  expect(screen.getByText('No shifts assigned to you yet.')).toBeInTheDocument()
})
