import { expect, test } from '@playwright/test'
import { signIn } from './helpers'

/**
 * The money pipeline end to end, walked through as a real browser would: a donor gives to the
 * Jamuna flood, a coordinator procures resources for a real camp against that fund, the
 * coordinator's unaccounted-funds report reflects it, and the donor's own impact view shows the
 * aggregated Donation → Camp chain with only camp/resource/amount — nothing from the family or
 * victim tables anywhere in the page.
 */
test('a donation flows through procurement into the coordinator report and the donor impact view', async ({ page }) => {
  await signIn(page, 'donor')

  await expect(page.getByRole('heading', { name: 'Make a donation' })).toBeVisible()
  await page.getByLabel('Disaster').selectOption({ label: 'Jamuna River Flood' })
  await page.getByPlaceholder('0').fill('750')
  await page.getByRole('button', { name: 'Donate' }).click()

  // The donation form has no visible success state of its own; the impact chain appearing (once
  // it refetches) is the proof the donation landed.
  await expect(page.getByRole('heading', { name: 'Your impact' })).toBeVisible()
  await expect(page.getByText('You gave')).toBeVisible()

  await page.getByRole('button', { name: 'Sign out' }).click()
  await signIn(page, 'coordinator')

  await page.getByRole('button', { name: /funds/i }).click()
  await expect(page.getByRole('heading', { name: 'Procure resources' })).toBeVisible()

  await page.getByRole('combobox', { name: 'Disaster' }).selectOption({ label: 'Jamuna River Flood' })
  await page.getByRole('combobox', { name: 'Camp' }).selectOption({ label: 'Kurigram Sadar Govt College Shelter' })
  await page.getByRole('combobox', { name: 'Resource' }).selectOption('WATER')
  await page.getByPlaceholder('৳').fill('200')
  await page.getByRole('button', { name: 'Procure' }).click()

  // The report table renders a row for Jamuna once at least one donation exists; its
  // "Unaccounted" cell must never go negative — the hard ledger invariant this ticket exists for.
  const jamunaRow = page.getByRole('row').filter({ hasText: 'Jamuna River Flood' });
  await expect(jamunaRow).toBeVisible()
  await expect(jamunaRow.getByRole('cell').last()).not.toContainText('-')

  await page.getByRole('button', { name: 'Sign out' }).click()
  await signIn(page, 'donor')

  await expect(page.getByRole('heading', { name: 'Your impact' })).toBeVisible()
  await expect(page.getByText('Kurigram Sadar Govt College Shelter')).toBeVisible()
  await expect(page.getByText(/victim|family|member/i)).toHaveCount(0)
})
