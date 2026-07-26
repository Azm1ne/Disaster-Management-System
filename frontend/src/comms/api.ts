// Ticket 12 in-app communication: shapes returned by /allocations/{id}/notes, /broadcasts, and
// /dms, and thin fetchers over authFetch — mirrors frontend/src/alerts/api.ts.

export interface NoteView {
  authorUserId: number
  body: string
  createdAt: string
}

export type BroadcastTargetRole = 'CAMP_MANAGER' | 'VOLUNTEER'

export interface BroadcastView {
  id: number
  senderUserId: number
  targetRole: BroadcastTargetRole
  bodyEn: string
  bodyBn: string
  createdAt: string
}

export interface BroadcastReadView {
  userId: number
  readAt: string
}

export interface ContactView {
  userId: number
  role: string
  nameEn: string
  nameBn: string
}

export interface DirectMessageView {
  id: number
  senderUserId: number
  recipientUserId: number
  body: string
  createdAt: string
}

type Fetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>

// ---- Tier 1: allocation case notes ----

export async function fetchAllocationNotes(authFetch: Fetcher, allocationId: number): Promise<NoteView[]> {
  const response = await authFetch(`/api/allocations/${allocationId}/notes`)
  if (!response.ok) throw new Error(`allocation_notes_read_failed_${response.status}`)
  return (await response.json()) as NoteView[]
}

export async function addAllocationNote(
  authFetch: Fetcher,
  allocationId: number,
  body: string,
): Promise<NoteView> {
  const response = await authFetch(`/api/allocations/${allocationId}/notes`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ body }),
  })
  if (!response.ok) throw new Error(`allocation_note_failed_${response.status}`)
  return (await response.json()) as NoteView
}

// ---- Tier 2: broadcasts ----

export async function fetchBroadcasts(authFetch: Fetcher): Promise<BroadcastView[]> {
  const response = await authFetch('/api/broadcasts')
  if (!response.ok) throw new Error(`broadcasts_read_failed_${response.status}`)
  return (await response.json()) as BroadcastView[]
}

export async function sendBroadcast(
  authFetch: Fetcher,
  input: { targetRole: BroadcastTargetRole; bodyEn: string; bodyBn: string },
): Promise<BroadcastView> {
  const response = await authFetch('/api/broadcasts', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
  if (!response.ok) throw new Error(`broadcast_send_failed_${response.status}`)
  return (await response.json()) as BroadcastView
}

export async function markBroadcastRead(authFetch: Fetcher, id: number): Promise<void> {
  const response = await authFetch(`/api/broadcasts/${id}/read`, { method: 'POST' })
  if (!response.ok) throw new Error(`broadcast_read_failed_${response.status}`)
}

export async function fetchBroadcastReceipts(authFetch: Fetcher, id: number): Promise<BroadcastReadView[]> {
  const response = await authFetch(`/api/broadcasts/${id}/receipts`)
  if (!response.ok) throw new Error(`broadcast_receipts_read_failed_${response.status}`)
  return (await response.json()) as BroadcastReadView[]
}

// ---- Tier 3: direct messages ----

export async function fetchDmContacts(authFetch: Fetcher): Promise<ContactView[]> {
  const response = await authFetch('/api/dms/contacts')
  if (!response.ok) throw new Error(`dm_contacts_read_failed_${response.status}`)
  return (await response.json()) as ContactView[]
}

export async function fetchDmThread(authFetch: Fetcher, otherUserId: number): Promise<DirectMessageView[]> {
  const response = await authFetch(`/api/dms/thread/${otherUserId}`)
  if (!response.ok) throw new Error(`dm_thread_read_failed_${response.status}`)
  return (await response.json()) as DirectMessageView[]
}

export async function sendDm(
  authFetch: Fetcher,
  recipientUserId: number,
  body: string,
): Promise<DirectMessageView> {
  const response = await authFetch('/api/dms', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ recipientUserId, body }),
  })
  if (!response.ok) throw new Error(`dm_send_failed_${response.status}`)
  return (await response.json()) as DirectMessageView
}
