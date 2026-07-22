import { expect, test } from 'vitest'
import { POLL_INTERVAL_MS, refetchIntervalFor } from '@/realtime/RealtimeProvider'

// The polling fallback in one decision: while the socket is pushing updates there is nothing to
// poll for, and the moment it drops the app must keep data reasonably fresh on its own.

test('a live socket means no polling', () => {
  expect(refetchIntervalFor(true)).toBe(false)
})

test('a dropped socket falls back to polling', () => {
  expect(refetchIntervalFor(false)).toBe(POLL_INTERVAL_MS)
})
