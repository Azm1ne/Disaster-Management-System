import { expect, test } from '@playwright/test'
import { signIn } from './helpers'

/**
 * Victim registration, dual-source arrival, and reunification search, walked through as a
 * real browser would exercise them. The demo Victim account only has one registration ever
 * (a real DB constraint, not reset between runs), so each step is idempotent: it registers or
 * confirms only if that has not already happened, exactly like a person returning to a
 * half-completed flow — which is also a real scenario this feature must handle.
 */
test('a victim registers, confirms arrival, and can be found by reunification search', async ({ page }) => {
  await signIn(page, 'victim')

  const registerHeading = page.getByRole('heading', { name: 'Register your family' })
  if (await registerHeading.isVisible().catch(() => false)) {
    await page.getByLabel('Family or group name').fill('Rahman Household')
    await page.getByLabel('Destination camp').selectOption({ label: 'Kurigram Sadar Govt College Shelter' })
    await page.getByPlaceholder('Nickname').fill('Abbu')
    await page.getByRole('button', { name: 'Register' }).click()
  }
  await expect(page.getByText('Rahman Household')).toBeVisible()

  const arriveButton = page.getByRole('button', { name: "I've arrived at the camp" })
  if (await arriveButton.isVisible().catch(() => false)) {
    await arriveButton.click()
  }
  await expect(page.getByText(/Arriving|Arrived/)).toBeVisible()

  await page.getByRole('button', { name: 'Sign out' }).click()
  await signIn(page, 'camp_manager')

  const confirmButton = page.getByRole('button', { name: 'Confirm arrival' }).first()
  if (await confirmButton.isVisible().catch(() => false)) {
    await confirmButton.click()
  }
  await expect(page.getByText('Rahman Household')).toBeVisible()

  // Reunification: no login, search only, and never a roster.
  await page.getByRole('button', { name: 'Sign out' }).click()
  await page.goto('/locator')
  await page.getByLabel('Search by family or group name').fill('Rahman')
  await expect(page.getByText('Rahman Household')).toBeVisible()
  await expect(page.getByText('Abbu')).toHaveCount(0)
})
