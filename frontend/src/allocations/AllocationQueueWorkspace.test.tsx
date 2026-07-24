import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, expect, test, vi } from 'vitest'
import { AllocationQueueWorkspace } from '@/allocations/AllocationQueueWorkspace'
import type { AllocationSummary } from '@/allocations/api'
import i18n from '@/i18n'

const allocation: AllocationSummary = {
  id: 1,
  resourceType: 'WATER',
  sourceCampId: 2,
  targetCampId: 5,
  recommendedQuantity: 300,
  decidedQuantity: null,
  status: 'RECOMMENDED',
  severityScore: 0.4,
  shortageScore: 0.9,
  populationScore: 0.2,
  fairnessScore: 0.6,
  priorityScore: 0.635,
  generatedAtTick: 10,
  decidedAtTick: null,
  canAct: true,
  createdAt: '2026-07-24T00:00:00Z',
  updatedAt: '2026-07-24T00:00:00Z',
}

const transition = vi.fn()
const allocations = vi.fn<() => AllocationSummary[] | undefined>(() => [allocation])
vi.mock('@/allocations/useAllocations', () => ({
  useAllocations: () => allocations(),
  useTransitionAllocation: () => transition,
}))

beforeEach(async () => {
  vi.clearAllMocks()
  allocations.mockReturnValue([allocation])
  await i18n.changeLanguage('en')
})

test('an allocation card shows its per-factor breakdown, not a bare priority number', () => {
  render(<AllocationQueueWorkspace apiRole="COORDINATOR" />)

  expect(screen.getByText('Camp 2 → Camp 5')).toBeInTheDocument()
  expect(screen.getByText('Water')).toBeInTheDocument()
  expect(screen.getByText('0.64')).toBeInTheDocument()
  expect(screen.getByText('Shortage urgency')).toBeInTheDocument()
  expect(screen.getByText('Medical severity')).toBeInTheDocument()
  expect(screen.getByText('Population')).toBeInTheDocument()
  expect(screen.getByText('Fairness')).toBeInTheDocument()
})

test('a coordinator can approve with a single click', () => {
  render(<AllocationQueueWorkspace apiRole="COORDINATOR" />)

  fireEvent.click(screen.getByText('Approve'))

  expect(transition).toHaveBeenCalledWith(1, { toStatus: 'APPROVED', quantity: undefined })
})

test('modify is disabled until a positive quantity is entered', () => {
  render(<AllocationQueueWorkspace apiRole="COORDINATOR" />)

  const modifyButton = screen.getByText('Modify')
  expect(modifyButton).toBeDisabled()
})

test('reject requires a second confirming click before it fires', () => {
  render(<AllocationQueueWorkspace apiRole="COORDINATOR" />)

  fireEvent.click(screen.getByText('Reject'))
  expect(transition).not.toHaveBeenCalled()

  fireEvent.click(screen.getByText('Confirm reject'))
  expect(transition).toHaveBeenCalledWith(1, { toStatus: 'REJECTED', quantity: undefined })
})

test('a Camp Manager sees no action buttons — read-only', () => {
  // The real backend never sets canAct for a Camp Manager (AllocationService.canAct is
  // Coordinator/Admin-only); this mirrors that server-side scoping instead of re-deciding
  // visibility from apiRole in the component.
  allocations.mockReturnValue([{ ...allocation, canAct: false }])
  render(<AllocationQueueWorkspace apiRole="CAMP_MANAGER" />)

  expect(screen.queryByText('Approve')).not.toBeInTheDocument()
  expect(screen.queryByText('Modify')).not.toBeInTheDocument()
  expect(screen.queryByText('Reject')).not.toBeInTheDocument()
  expect(screen.getByText('Allocations approved for your camp.')).toBeInTheDocument()
})

test('nothing is shown before allocations have loaded', () => {
  allocations.mockReturnValue(undefined)

  const { container } = render(<AllocationQueueWorkspace apiRole="COORDINATOR" />)

  expect(container).toBeEmptyDOMElement()
})
