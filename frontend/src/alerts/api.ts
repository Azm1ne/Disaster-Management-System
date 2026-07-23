// Alert lifecycle: shapes returned by /alerts, and thin fetchers over authFetch — mirrors
// frontend/src/world/api.ts.

export type AlertType =
  | 'RESOURCE_SHORTAGE'
  | 'MEDICAL_EMERGENCY'
  | 'SECURITY_INCIDENT'
  | 'INFRASTRUCTURE_DAMAGE'

export type AlertStatus = 'NEW' | 'ACKNOWLEDGED' | 'IN_PROGRESS' | 'RESOLVED' | 'ESCALATED' | 'CLOSED'

export interface AlertSummary {
  id: number
  type: AlertType
  status: AlertStatus
  campId: number
  description: string
  resourceType: 'WATER' | 'FOOD' | 'MEDICAL' | null
  raisedByUserId: number
  raisedAtTick: number
  slaDeadlineTick: number
  canAct: boolean
  createdAt: string
  updatedAt: string
}

export interface TransitionView {
  fromStatus: AlertStatus
  toStatus: AlertStatus
  actorUserId: number | null
  note: string | null
  atTick: number
  createdAt: string
}

export interface NoteView {
  authorUserId: number
  body: string
  createdAt: string
}

export interface AlertDetail {
  summary: AlertSummary
  transitions: TransitionView[]
  notes: NoteView[]
}

type Fetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>

export async function fetchAlerts(authFetch: Fetcher): Promise<AlertSummary[]> {
  const response = await authFetch('/api/alerts')
  if (!response.ok) throw new Error(`alerts_read_failed_${response.status}`)
  return (await response.json()) as AlertSummary[]
}

export async function fetchAlertDetail(authFetch: Fetcher, id: number): Promise<AlertDetail> {
  const response = await authFetch(`/api/alerts/${id}`)
  if (!response.ok) throw new Error(`alert_detail_read_failed_${response.status}`)
  return (await response.json()) as AlertDetail
}

export async function createAlert(
  authFetch: Fetcher,
  input: { type: AlertType; campId: number; description: string },
): Promise<AlertSummary> {
  const response = await authFetch('/api/alerts', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
  if (!response.ok) throw new Error(`alert_create_failed_${response.status}`)
  return (await response.json()) as AlertSummary
}

export async function transitionAlert(
  authFetch: Fetcher,
  id: number,
  toStatus: AlertStatus,
  note?: string,
): Promise<AlertSummary> {
  const response = await authFetch(`/api/alerts/${id}/transition`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ toStatus, note }),
  })
  if (!response.ok) throw new Error(`alert_transition_failed_${response.status}`)
  return (await response.json()) as AlertSummary
}

export async function addAlertNote(authFetch: Fetcher, id: number, body: string): Promise<NoteView> {
  const response = await authFetch(`/api/alerts/${id}/notes`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ body }),
  })
  if (!response.ok) throw new Error(`alert_note_failed_${response.status}`)
  return (await response.json()) as NoteView
}

export async function raiseDemoAlert(authFetch: Fetcher): Promise<AlertSummary> {
  const response = await authFetch('/api/alerts/demo', { method: 'POST' })
  if (!response.ok) throw new Error(`alert_demo_failed_${response.status}`)
  return (await response.json()) as AlertSummary
}
