import { render, screen } from '@testing-library/react'
import { beforeEach, expect, test, vi } from 'vitest'
import { ForecastWorkspace } from '@/forecasts/ForecastWorkspace'
import type { ForecastView } from '@/forecasts/api'
import i18n from '@/i18n'

const forecast: ForecastView = {
  campId: 1,
  campCode: 'jam-kurigram-sadar',
  campNameEn: 'Kurigram Sadar Govt College Shelter',
  campNameBn: 'কুড়িগ্রাম সদর সরকারি কলেজ আশ্রয়কেন্দ্র',
  resourceType: 'WATER',
  currentQuantity: 2394.5,
  ratePerTick: -42.5,
  ticksRemainingEstimate: 56,
  ticksRemainingWorstCase: 40,
  ticksRemainingBestCase: 80,
  confidenceScore: 0.82,
  confidenceLevel: 'HIGH',
  latestObservedTick: 12,
  sampleCount: 8,
}

const forecasts = vi.fn<() => ForecastView[] | undefined>(() => [forecast])
vi.mock('@/forecasts/useForecasts', () => ({ useForecasts: () => forecasts() }))

beforeEach(async () => {
  vi.clearAllMocks()
  forecasts.mockReturnValue([forecast])
  await i18n.changeLanguage('en')
})

test('a forecast card shows its explainable breakdown, not a bare number', () => {
  render(<ForecastWorkspace />)

  expect(screen.getByText('Kurigram Sadar Govt College Shelter')).toBeInTheDocument()
  expect(screen.getByText('Water')).toBeInTheDocument()
  expect(screen.getByText(/Consumption rate/)).toBeInTheDocument()
  expect(screen.getByText(/Time to exhaustion/)).toBeInTheDocument()
  expect(screen.getByText(/Range: 40–80/)).toBeInTheDocument()
  expect(screen.getByText(/Confidence: High/)).toBeInTheDocument()
  expect(screen.getByText(/Latest reading: tick 12/)).toBeInTheDocument()
})

test('a stable resource says so instead of showing a bare exhaustion number', () => {
  forecasts.mockReturnValue([{ ...forecast, ticksRemainingEstimate: null }])

  render(<ForecastWorkspace />)

  expect(screen.getByText('Not depleting')).toBeInTheDocument()
})

test('nothing is shown before forecasts have loaded', () => {
  forecasts.mockReturnValue(undefined)

  const { container } = render(<ForecastWorkspace />)

  expect(container).toBeEmptyDOMElement()
})
