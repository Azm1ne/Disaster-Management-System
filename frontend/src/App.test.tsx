import { render, screen } from '@testing-library/react'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import App from './App'
import './i18n'

beforeEach(() => {
  // The placeholder probes the API on mount; stub it so the render test is deterministic.
  vi.stubGlobal(
    'fetch',
    vi.fn(() =>
      Promise.resolve({ ok: true, json: () => Promise.resolve({ status: 'UP' }) }),
    ),
  )
})

afterEach(() => {
  vi.unstubAllGlobals()
})

test('renders the application name', () => {
  render(<App />)
  expect(
    screen.getByRole('heading', { name: 'Disaster Management System' }),
  ).toBeInTheDocument()
})
