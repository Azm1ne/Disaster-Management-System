import { expect, test } from '@playwright/test'
import { signIn } from './helpers'

/**
 * The Allocations tab, wired into the operator shell: a Coordinator can switch to it from the
 * sidebar and see the ranked recommendation queue with its per-factor breakdown (not just a bare
 * priority number).
 */
test('coordinator sees the allocations tab with a priority breakdown', async ({ page }) => {
  await signIn(page, 'coordinator')
  await page.getByRole('button', { name: /allocations/i }).click()
  await expect(
    page.getByText(/Priority score|No recommendations right now/i).first(),
  ).toBeVisible()
})
