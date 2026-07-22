import { expect, type Page } from '@playwright/test'

/** The shared password for every seeded demo account (see README). */
export const DEMO_PASSWORD = 'relief2026'

/** Each seeded role, the label its workspace shows, and where it should land. */
export const ROLE_LABELS: Record<string, { label: string; shell: 'operator' | 'field'; path: string }> =
  {
    coordinator: { label: 'Relief Coordinator', shell: 'operator', path: '/coordinator' },
    camp_manager: { label: 'Camp Manager', shell: 'operator', path: '/camp' },
    admin: { label: 'Administrator', shell: 'operator', path: '/admin' },
    donor: { label: 'Donor', shell: 'field', path: '/donor' },
    volunteer: { label: 'Volunteer', shell: 'field', path: '/volunteer' },
    victim: { label: 'Victim', shell: 'field', path: '/victim' },
    ngo: { label: 'NGO', shell: 'field', path: '/ngo' },
  }

/** Signs in through the real form and waits until the role's workspace has rendered. */
export async function signIn(page: Page, username: string) {
  await page.goto('/login')
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(DEMO_PASSWORD)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page).toHaveURL(new RegExp(`${ROLE_LABELS[username].path}$`))
}

/**
 * Drives the simulation straight through the API. Used to put the clock in a known state around
 * a test, so a spec never depends on where a previous one left the shared run.
 */
export async function resetSimulation(page: Page) {
  const token = await page.evaluate(() => localStorage.getItem('dms.access'))
  const response = await page.request.post('http://localhost:8080/simulation/reset', {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(response.ok()).toBeTruthy()
}
