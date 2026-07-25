import { expect, test, type Page } from '@playwright/test'
import { signIn } from './helpers'

/**
 * Volunteer matching end to end (ticket 09): a coordinator scores and push-assigns a volunteer to
 * a task, a different volunteer self-accepts a separate open shift, and that volunteer sees a
 * route to it. Alerts (and the tasks they generate) are never reset between runs — like
 * alerts.spec.ts, each alert here is raised directly through the real API so this run always acts
 * on a task it just created, not one an earlier run left behind.
 */

async function authToken(page: Page): Promise<string> {
  return (await page.evaluate(() => localStorage.getItem('dms.access'))) as string
}

async function ownCampId(page: Page, token: string): Promise<number> {
  const me = await page.request.get('http://localhost:8080/me', {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(me.ok()).toBeTruthy()
  const { campIds } = (await me.json()) as { campIds: number[] }
  if (campIds.length > 0) return campIds[0]
  const disasters = await page.request.get('http://localhost:8080/world/disasters', {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(disasters.ok()).toBeTruthy()
  const world = (await disasters.json()) as { camps: { id: number }[] }[]
  return world[0].camps[0].id
}

/** Raises an alert straight through the API — the real production pipeline
 * (VolunteerTaskGenerationService) turns it into a task synchronously, so the task exists by the
 * time this returns. Returns the new task's id. */
async function raiseAlertAndGetTaskId(
  page: Page,
  token: string,
  type: string,
  campId: number,
): Promise<number> {
  const alertResponse = await page.request.post('http://localhost:8080/alerts', {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: { type, campId, description: `e2e ${type} ${Date.now()}` },
  })
  expect(alertResponse.ok()).toBeTruthy()
  const alert = (await alertResponse.json()) as { id: number }

  const tasksResponse = await page.request.get('http://localhost:8080/volunteers/tasks', {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(tasksResponse.ok()).toBeTruthy()
  const tasks = (await tasksResponse.json()) as { id: number; alertId: number }[]
  const task = tasks.find((t) => t.alertId === alert.id)
  if (!task) throw new Error(`No volunteer task generated for alert ${alert.id}`)
  return task.id
}

test('a coordinator ranks candidates and push-assigns the best fit', async ({ page }) => {
  await signIn(page, 'coordinator')
  const token = await authToken(page)
  const campId = await ownCampId(page, token)
  const taskId = await raiseAlertAndGetTaskId(page, token, 'MEDICAL_EMERGENCY', campId)

  await page.getByRole('button', { name: /volunteers/i }).click()
  const card = page.locator(`[data-task-id="${taskId}"]`)
  await expect(card).toBeVisible()

  await card.getByRole('button', { name: /show candidates/i }).click()
  // Sabbir Rahman is seeded ~1km from jam-kurigram-sadar with the MEDICAL skill — the clear
  // best fit — so it's the first (and typically only meaningfully-ranked) name shown.
  await expect(card.getByText('Sabbir Rahman')).toBeVisible()

  await card.getByRole('button', { name: /^assign$/i }).first().click()
  await expect(card.getByText('Assigned', { exact: true })).toBeVisible()
  await expect(card.getByText(/Sabbir Rahman/)).toBeVisible()
})

test('a volunteer self-accepts an open shift and sees a route to it', async ({ page, browser }) => {
  const coordinatorContext = await browser.newContext()
  const coordinatorPage = await coordinatorContext.newPage()
  await signIn(coordinatorPage, 'coordinator')
  const token = await authToken(coordinatorPage)
  const campId = await ownCampId(coordinatorPage, token)
  const taskId = await raiseAlertAndGetTaskId(coordinatorPage, token, 'RESOURCE_SHORTAGE', campId)
  await coordinatorContext.close()

  await signIn(page, 'volunteer')
  const card = page.locator(`[data-task-id="${taskId}"]`)
  await expect(async () => {
    await page.reload()
    await expect(card).toBeVisible({ timeout: 2000 })
  }).toPass({ timeout: 15_000 })

  await card.getByRole('button', { name: /accept shift/i }).click()

  const myShiftCard = page.locator(`[data-task-id="${taskId}"]`)
  await expect(async () => {
    await expect(myShiftCard.getByRole('button', { name: /view route/i })).toBeVisible({ timeout: 2000 })
  }).toPass({ timeout: 15_000 })

  await myShiftCard.getByRole('button', { name: /view route/i }).click()
  await expect(
    myShiftCard.getByText(/road route|straight-line estimate/i),
  ).toBeVisible({ timeout: 15_000 })
})
