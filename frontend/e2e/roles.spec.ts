import { expect, test } from '@playwright/test'
import { ROLE_LABELS, signIn } from './helpers'

/**
 * Per-role smoke: every seeded role can actually sign in and land on the workspace meant for it.
 * This is the check that the whole vertical — API, token, role routing, shell, i18n — holds
 * together in a real browser for each of the eight roles, not just the one we happen to develop in.
 */
test.describe('every role reaches its own workspace', () => {
  for (const [username, { label, shell, path }] of Object.entries(ROLE_LABELS)) {
    test(`${username} signs in and lands on the ${shell} shell`, async ({ page }) => {
      await signIn(page, username)

      await expect(page).toHaveURL(new RegExp(`${path}$`))
      await expect(page.getByText(label).first()).toBeVisible()

      if (shell === 'operator') {
        // The situation-room ribbon, which must always carry the DEMO badge.
        await expect(page.getByText('DEMO').first()).toBeVisible()
        // The world map — only the operator shell renders one.
        await expect(page.locator('.leaflet-container').first()).toBeVisible()
      } else if (shell === 'field') {
        await expect(page.getByRole('heading', { name: /Welcome,/ })).toBeVisible()
      } else {
        // Central Authority's bare approval inbox: no shell chrome at all, so the negative space
        // is the assertion — no DEMO badge, no map, just the queue.
        await expect(page.getByText('DEMO')).toHaveCount(0)
        await expect(page.locator('.leaflet-container')).toHaveCount(0)
      }

      // EN<->বাংলা toggle: its own label names the language it would switch TO, so clicking it
      // and re-reading confirms real strings changed, regardless of which language we started in.
      const toggle = page.getByRole('button', { name: /Switch language to/i })
      await expect(toggle).toBeVisible()
      const beforeLang = await page.locator('html').getAttribute('lang')
      const beforeLabel = await toggle.textContent()

      await toggle.click()

      await expect(async () => {
        expect(await page.locator('html').getAttribute('lang')).not.toBe(beforeLang)
      }).toPass()
      await expect(toggle).not.toHaveText(beforeLabel ?? '')
    })
  }
})

test('a signed-out visitor is sent to sign in', async ({ page }) => {
  await page.goto('/coordinator')

  await expect(page.getByRole('heading', { name: 'Sign in to the operation' })).toBeVisible()
})

test('the public locator needs no account', async ({ page }) => {
  await page.goto('/locator')

  await expect(page.getByRole('heading', { name: 'Find a shelter' })).toBeVisible()
  // The locator is the one place camp data is shown to nobody in particular, so it must never
  // leak operational detail.
  await expect(page.getByText(/capacity/i)).toHaveCount(0)
})
