import { expect, test } from '@playwright/test'
import { signIn } from './helpers'

/**
 * The Forecasts tab, wired into the operator shell: a Coordinator can switch to it from the
 * sidebar and see the explainable per-camp/resource breakdown (not just a bare number).
 */
test('coordinator sees the forecasts tab with an explainable breakdown', async ({ page }) => {
  await signIn(page, 'coordinator')
  await page.getByRole('button', { name: /forecasts/i }).click()
  await expect(page.getByText(/Consumption rate/i).first()).toBeVisible()
  await expect(page.getByText(/Time to exhaustion|Not depleting/i).first()).toBeVisible()
})
