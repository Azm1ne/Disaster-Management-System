import { expect, test, type Page } from '@playwright/test'
import { signIn } from './helpers'

/**
 * Ticket 12's three tiers against the real running stack: a case note attached to a real
 * allocation, a broadcast whose read receipt actually records once the recipient opens it, and a
 * DM permitted along a real operational relationship vs. refused along a fabricated one.
 *
 * Alerts/allocations/broadcasts/DMs are never reset between runs, so every unique string below is
 * stamped with a nonce — the same convention `alerts.spec.ts` uses — so a re-run can find the
 * exact row it just made instead of an identical-looking leftover.
 */
function unique(base: string): string {
  return `${base} [${Date.now()}-${Math.random().toString(36).slice(2, 8)}]`
}

async function tokenFor(page: Page): Promise<string> {
  const token = await page.evaluate(() => localStorage.getItem('dms.access'))
  if (!token) throw new Error('Not signed in')
  return token
}

/** Triggers the real allocation-generation pipeline against two real camps, exactly like
 * `AllocationDemoTriggerIntegrationTest`, so there is a real `AllocationDecision` row to attach a
 * case note to. */
async function triggerDemoAllocation(page: Page): Promise<void> {
  const token = await tokenFor(page)
  const disasters = await page.request.get('http://localhost:8080/world/disasters', {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(disasters.ok()).toBeTruthy()
  const world = (await disasters.json()) as { camps: { id: number; code: string }[] }[]
  const camps = world.flatMap((disaster) => disaster.camps)
  const shortageCampId = camps.find((c) => c.code === 'jam-fulchhari')!.id
  const surplusCampId = camps.find((c) => c.code === 'jam-sundarganj')!.id

  const response = await page.request.post(
    `http://localhost:8080/allocations/demo/${shortageCampId}/${surplusCampId}/MEDICAL`,
    { headers: { Authorization: `Bearer ${token}` } },
  )
  expect(response.ok()).toBeTruthy()
}

test('a coordinator attaches a case note to a real allocation (extending ticket 06 to transfers)', async ({
  page,
}) => {
  await signIn(page, 'coordinator')
  await triggerDemoAllocation(page)

  await page.getByRole('button', { name: /^allocations$/i }).click()
  await expect(page.getByText(/Priority score/i).first()).toBeVisible()

  const noteText = unique('Confirmed the convoy departed')
  await page.getByRole('button', { name: /show notes/i }).first().click()
  await page.getByPlaceholder('Add a note').first().fill(noteText)
  await page.getByRole('button', { name: 'Add', exact: true }).first().click()

  await expect(page.getByText(noteText)).toBeVisible()
})

test('a broadcast to camp managers records a read receipt the coordinator can see', async ({ page, browser }) => {
  const coordinatorPage = page
  await signIn(coordinatorPage, 'coordinator')
  await coordinatorPage.getByRole('button', { name: /^comms$/i }).click()

  const bodyEn = unique('Convoy delayed 2 hours')
  await coordinatorPage.getByPlaceholder('Message (English)').fill(bodyEn)
  await coordinatorPage.getByPlaceholder('বার্তা (বাংলা)').fill('কনভয় বিলম্বিত')
  await coordinatorPage.getByRole('button', { name: 'Broadcast', exact: true }).click()

  await expect(coordinatorPage.getByText(bodyEn)).toBeVisible()

  const campManagerContext = await browser.newContext()
  const campManagerPage = await campManagerContext.newPage()
  await signIn(campManagerPage, 'camp_manager')
  await campManagerPage.getByRole('button', { name: /^comms$/i }).click()
  await expect(campManagerPage.getByText(bodyEn)).toBeVisible()
  // Opening the row is the read signal — the same "opening it is reading it" convention as email.
  // Wait for the actual /read response before tearing the context down, so the request isn't
  // aborted mid-flight.
  const readResponse = campManagerPage.waitForResponse((res) => res.url().includes('/read') && res.request().method() === 'POST')
  await campManagerPage.getByText(bodyEn).click()
  await readResponse
  await campManagerContext.close()

  // Back on the coordinator's page: expand the same row and see the receipt land. Uses `toPass`
  // rather than a single `isVisible()` branch, since the receipt fetch only starts once expanded
  // and needs a moment to resolve.
  await coordinatorPage.getByText(bodyEn).click()
  await expect(async () => {
    await expect(coordinatorPage.getByText(/1 read/)).toBeVisible()
  }).toPass({ timeout: 10_000 })
})

test('a coordinator can DM a camp manager (a real operational relationship)', async ({ page }) => {
  await signIn(page, 'coordinator')
  await page.getByRole('button', { name: /^comms$/i }).click()

  await page.getByText('Anwar Hossain').click()
  const messageText = unique('Status on water resupply?')
  await page.getByPlaceholder('Write a message').fill(messageText)
  await page.getByRole('button', { name: 'Send', exact: true }).click()

  await expect(page.getByText(messageText)).toBeVisible()
})

test('a DM refused along a fabricated relationship is rejected server-side, not just hidden by the UI', async ({
  page,
}) => {
  await signIn(page, 'donor')
  const token = await tokenFor(page)

  // A Donor has no DM contacts at all, so the UI never offers this — assert the server itself
  // refuses it, which is what the acceptance criteria actually asks for.
  const campManagerLogin = await page.request.post('http://localhost:8080/auth/login', {
    data: { username: 'camp_manager', password: 'relief2026' },
  })
  expect(campManagerLogin.ok()).toBeTruthy()
  const { accessToken: campManagerToken } = (await campManagerLogin.json()) as { accessToken: string }
  const campManagerMe = await page.request.get('http://localhost:8080/me', {
    headers: { Authorization: `Bearer ${campManagerToken}` },
  })
  const { userId: campManagerId } = (await campManagerMe.json()) as { userId: number }

  const response = await page.request.post('http://localhost:8080/dms', {
    headers: { Authorization: `Bearer ${token}` },
    data: { recipientUserId: campManagerId, body: 'This should not be allowed' },
  })
  expect(response.status()).toBe(403)
})
