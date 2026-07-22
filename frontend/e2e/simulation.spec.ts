import { expect, test } from '@playwright/test'
import { resetSimulation, signIn } from './helpers'

/**
 * The ticket's central claim, verified in a real browser: the world moves on its own and the
 * screen follows it over the live socket, with no refresh and no polling gap. Also checks that
 * the DEMO control is a real boundary — visible and usable only where it should be.
 */

async function openControl(page: import('@playwright/test').Page) {
  await page.getByRole('button', { name: /Simulation control/ }).click()
  await expect(page.getByRole('complementary', { name: 'Simulation control' })).toBeVisible()
}

/** The tick the DEMO panel is currently showing. */
async function currentTick(page: import('@playwright/test').Page): Promise<number> {
  const text = await page.getByText(/Tick \d+ of \d+/).innerText()
  return Number(text.match(/Tick (\d+)/)![1])
}

test.describe('the simulation drives the world live', () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page, 'coordinator')
    await resetSimulation(page)
  })

  test.afterEach(async ({ page }) => {
    await resetSimulation(page)
  })

  test('the control is badged DEMO and shows the scripted storyline', async ({ page }) => {
    await openControl(page)

    const panel = page.getByRole('complementary', { name: 'Simulation control' })
    await expect(panel.getByText('DEMO', { exact: true })).toBeVisible()
    // The run is a scripted story, so the panel names the phases rather than just a percentage.
    await expect(panel.getByText('Surge')).toBeVisible()
    await expect(panel.getByText('Overflow camp opens')).toBeVisible()
    await expect(panel.getByText('Relief convoy')).toBeVisible()
    await expect(panel.getByText('Recovery')).toBeVisible()
  })

  test('resuming advances the clock on screen, and pausing holds it', async ({ page }) => {
    await openControl(page)
    expect(await currentTick(page)).toBe(0)

    await page.getByRole('button', { name: 'Resume' }).click()
    // Nothing here reloads or refetches: the ticks arrive over the socket.
    await expect.poll(() => currentTick(page), { timeout: 15_000 }).toBeGreaterThan(2)

    await page.getByRole('button', { name: 'Pause' }).click()
    await expect(page.getByText('Paused', { exact: true })).toBeVisible()
    // A tick already in flight when Pause lands still arrives, so read the held value once the
    // clock has settled rather than the instant the click resolves.
    await page.waitForTimeout(1500)
    const held = await currentTick(page)

    // At 1x this would have advanced roughly three times had the pause not taken.
    await page.waitForTimeout(3000)
    expect(await currentTick(page)).toBe(held)
  })

  test('the map follows the simulation without a refresh', async ({ page }) => {
    const sheltered = page.getByText(/[\d,]+ sheltered/).first()
    const before = await sheltered.innerText()

    await openControl(page)
    await page.getByRole('button', { name: 'Resume' }).click()

    // The headline population is drawn from the pushed world, so it moving proves the map data
    // itself is live rather than the clock alone.
    await expect.poll(() => sheltered.innerText(), { timeout: 15_000 }).not.toBe(before)
  })

  test('resetting returns the world to the start of the story', async ({ page }) => {
    await openControl(page)
    await page.getByRole('button', { name: 'Resume' }).click()
    await expect.poll(() => currentTick(page), { timeout: 15_000 }).toBeGreaterThan(1)

    await page.getByRole('button', { name: 'Reset to start' }).click()

    await expect.poll(() => currentTick(page)).toBe(0)
  })
})

test('a camp manager sees their own camp, and cannot drive the simulation', async ({ page }) => {
  await signIn(page, 'camp_manager')

  // Their assigned camp arrives on its own topic — the one thing this role gets beyond the
  // shared picture.
  const myCamp = page.getByRole('region', { name: 'Your camp' })
  await expect(myCamp).toBeVisible()
  await expect(myCamp.getByText('Kurigram Sadar Govt College Shelter')).toBeVisible()
  // Real payload field names, rendered: this is what catches the UI reading a field the API
  // does not actually send.
  await expect(myCamp.getByText('Water')).toBeVisible()
  await expect(myCamp.getByText('Food')).toBeVisible()
  await expect(myCamp.getByText('Medical')).toBeVisible()

  await page.getByRole('button', { name: /Simulation control/ }).click()
  await expect(page.getByRole('button', { name: 'Resume' })).toBeDisabled()
  await expect(
    page.getByText('Only a Coordinator or Admin can drive the simulation.'),
  ).toBeVisible()
})

test('a field role never sees the simulation control at all', async ({ page }) => {
  await signIn(page, 'donor')

  await expect(page.getByRole('button', { name: /Simulation control/ })).toHaveCount(0)
})
