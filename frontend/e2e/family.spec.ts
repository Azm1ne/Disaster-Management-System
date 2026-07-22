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

  // Signing in only waits for the URL, and the panel renders nothing until it knows whether a
  // group exists — so settle into one of its two states before branching. `isVisible()` never
  // waits, and answering it mid-load silently skips the registration this test is here to do.
  const registerHeading = page.getByRole('heading', { name: 'Register your family' })
  const registeredName = page.getByText('Rahman Household')
  await expect(registerHeading.or(registeredName)).toBeVisible()
  if (await registerHeading.isVisible()) {
    await page.getByLabel('Family or group name').fill('Rahman Household')
    await page.getByLabel('Destination camp').selectOption({ label: 'Kurigram Sadar Govt College Shelter' })
    await page.getByPlaceholder('Nickname').fill('Abbu')
    await page.getByRole('button', { name: 'Register' }).click()
  }
  await expect(registeredName).toBeVisible()

  const arriveButton = page.getByRole('button', { name: "I've arrived at the camp" })
  if (await arriveButton.isVisible().catch(() => false)) {
    await arriveButton.click()
  }
  await expect(page.getByText(/Arriving|Arrived/)).toBeVisible()

  await page.getByRole('button', { name: 'Sign out' }).click()
  await signIn(page, 'camp_manager')

  // Same rule on the manager's side: the queue is empty until its own query resolves, so wait
  // for the group's row before asking whether it still needs confirming.
  await expect(page.getByText('Rahman Household')).toBeVisible()
  const rahmanRow = page.getByRole('listitem').filter({ hasText: 'Rahman Household' })
  const confirmButton = rahmanRow.getByRole('button', { name: 'Confirm arrival' })
  if (await confirmButton.isVisible()) {
    await confirmButton.click()
  }
  // The button renders only while the manager's stamp is missing, so its absence is the stamp.
  await expect(confirmButton).toHaveCount(0)

  // Reunification: no login, search only, and never a roster.
  await page.getByRole('button', { name: 'Sign out' }).click()
  await page.goto('/locator')
  await page.getByLabel('Search by family or group name').fill('Rahman')
  await expect(page.getByText('Rahman Household')).toBeVisible()
  await expect(page.getByText('Abbu')).toHaveCount(0)
})
