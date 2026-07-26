import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, expect, test, vi } from 'vitest'
import { AnomalyReviewWorkspace } from '@/anomalies/AnomalyReviewWorkspace'
import type { AnomalyFlagView } from '@/anomalies/api'
import i18n from '@/i18n'

const flag: AnomalyFlagView = {
  id: 1,
  detectorType: 'ALLOCATION_BURST',
  score: 0.82,
  summary: 'Three allocations to Camp 5 within one tick.',
  innocentExplanation: 'A genuine simultaneous shortage across multiple resource types can look like a burst.',
  subjectIds: [10, 11, 12],
  detectedAtTick: 20,
  status: 'OPEN',
  reviewedByUserId: null,
  reviewNote: null,
  reviewedAt: null,
  createdAt: '2026-07-24T00:00:00Z',
}

const review = vi.fn()
const anomalies = vi.fn<() => AnomalyFlagView[] | undefined>(() => [flag])
vi.mock('@/anomalies/useAnomalies', () => ({
  useAnomalies: () => anomalies(),
  useReviewAnomaly: () => review,
}))

beforeEach(async () => {
  vi.clearAllMocks()
  anomalies.mockReturnValue([flag])
  await i18n.changeLanguage('en')
})

test('the empty state renders when there are no flags', () => {
  anomalies.mockReturnValue([])
  render(<AnomalyReviewWorkspace />)

  expect(screen.getByText('No anomalies flagged right now.')).toBeInTheDocument()
})

test('a flag renders with its innocent explanation visible', () => {
  render(<AnomalyReviewWorkspace />)

  expect(screen.getByText('Allocation burst')).toBeInTheDocument()
  expect(screen.getByText('Three allocations to Camp 5 within one tick.')).toBeInTheDocument()
  expect(
    screen.getByText('A genuine simultaneous shortage across multiple resource types can look like a burst.'),
  ).toBeInTheDocument()
  expect(screen.getByText('10, 11, 12')).toBeInTheDocument()
})

test('clicking Confirm reviews the flag as confirmed', () => {
  render(<AnomalyReviewWorkspace />)

  fireEvent.click(screen.getByText('Confirm'))

  expect(review).toHaveBeenCalledWith(1, { toStatus: 'CONFIRMED' })
})

test('clicking Dismiss reviews the flag as dismissed', () => {
  render(<AnomalyReviewWorkspace />)

  fireEvent.click(screen.getByText('Dismiss'))

  expect(review).toHaveBeenCalledWith(1, { toStatus: 'DISMISSED' })
})

test('a reviewed flag shows no action buttons', () => {
  anomalies.mockReturnValue([
    { ...flag, status: 'CONFIRMED', reviewedByUserId: 7, reviewedAt: '2026-07-25T00:00:00Z' },
  ])
  render(<AnomalyReviewWorkspace />)

  expect(screen.queryByText('Confirm')).not.toBeInTheDocument()
  expect(screen.queryByText('Dismiss')).not.toBeInTheDocument()
  expect(screen.getByText('Confirmed')).toBeInTheDocument()
})

test('nothing is shown before anomalies have loaded', () => {
  anomalies.mockReturnValue(undefined)

  const { container } = render(<AnomalyReviewWorkspace />)

  expect(container).toBeEmptyDOMElement()
})
