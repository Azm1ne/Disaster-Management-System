import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/auth/AuthContext'
import { createAlert, raiseDemoAlert, type AlertStatus, type AlertSummary, type AlertType } from '@/alerts/api'
import { useAlertDetail } from '@/alerts/useAlertDetail'
import { useAlerts } from '@/alerts/useAlerts'

const NEXT_STATUS: Record<AlertStatus, AlertStatus[]> = {
  NEW: ['ACKNOWLEDGED'],
  ACKNOWLEDGED: ['IN_PROGRESS'],
  IN_PROGRESS: ['RESOLVED', 'ESCALATED'],
  RESOLVED: ['CLOSED'],
  ESCALATED: ['IN_PROGRESS', 'CLOSED'],
  CLOSED: [],
}

const ALERT_TYPES: AlertType[] = [
  'RESOURCE_SHORTAGE',
  'MEDICAL_EMERGENCY',
  'SECURITY_INCIDENT',
  'INFRASTRUCTURE_DAMAGE',
]

/**
 * The alert lifecycle workspace: a list of what this role is entitled to see, and a detail
 * panel to drive it. Only legal next transitions render as buttons — the server is still the
 * boundary (see AlertService), this is just not offering a button that would 400/403.
 */
export function AlertWorkspace() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const alerts = useAlerts()
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const { detail, transition, addNote } = useAlertDetail(selectedId)
  const [raising, setRaising] = useState(false)

  const canRaiseDemo = user?.role === 'COORDINATOR' || user?.role === 'ADMIN'

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto p-4 sm:p-6">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-ink">{t('alertLifecycle.title')}</h2>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => setRaising(true)}
            className="inline-flex h-8 items-center rounded-full border border-line px-3 text-xs text-ink-muted hover:border-line-strong hover:text-ink"
          >
            {t('alertLifecycle.raise')}
          </button>
          {canRaiseDemo && <DemoAlertButton />}
        </div>
      </div>

      {raising && <RaiseAlertForm onDone={() => setRaising(false)} />}

      {!alerts?.length && <p className="text-sm text-ink-muted">{t('alertLifecycle.empty')}</p>}

      <ul className="flex flex-col gap-2">
        {alerts?.map((alert) => (
          <AlertRow key={alert.id} alert={alert} onSelect={() => setSelectedId(alert.id)} />
        ))}
      </ul>

      {detail && (
        <section className="mt-4 rounded-lg border border-line p-4">
          <h3 className="text-sm font-semibold text-ink">{t(`alertLifecycle.type.${detail.summary.type}`)}</h3>
          <p className="mt-1 text-sm text-ink-muted">{detail.summary.description}</p>
          <p className="mt-2 font-mono text-xs text-ink-muted">
            {t(`alertLifecycle.status.${detail.summary.status}`)}
          </p>

          {detail.summary.canAct && (
            <div className="mt-3 flex flex-wrap gap-2">
              {NEXT_STATUS[detail.summary.status].map((next) => (
                <button
                  key={next}
                  type="button"
                  onClick={() => void transition(next)}
                  className="inline-flex h-8 items-center rounded-full border border-line px-3 text-xs hover:border-line-strong"
                >
                  {t(`alertLifecycle.action.${next}`)}
                </button>
              ))}
            </div>
          )}

          <h4 className="mt-4 text-xs font-semibold text-ink-muted uppercase">{t('alertLifecycle.timeline')}</h4>
          <ul className="mt-1 flex flex-col gap-1">
            {detail.transitions.map((transitionRow, index) => (
              <li key={index} className="font-mono text-[11px] text-ink-muted">
                {t(`alertLifecycle.status.${transitionRow.fromStatus}`)} →{' '}
                {t(`alertLifecycle.status.${transitionRow.toStatus}`)}
                {transitionRow.actorUserId === null ? ` (${t('alertLifecycle.systemActor')})` : ''}
              </li>
            ))}
          </ul>

          <h4 className="mt-4 text-xs font-semibold text-ink-muted uppercase">{t('alertLifecycle.notes')}</h4>
          <ul className="mt-1 flex flex-col gap-1">
            {detail.notes.map((noteRow, index) => (
              <li key={index} className="text-sm text-ink">
                {noteRow.body}
              </li>
            ))}
          </ul>
          {detail.summary.canAct && <AddNoteForm onAdd={(body) => addNote(body)} />}
        </section>
      )}
    </div>
  )
}

function AlertRow({ alert, onSelect }: { alert: AlertSummary; onSelect: () => void }) {
  const { t } = useTranslation()
  return (
    <li>
      <button
        type="button"
        onClick={onSelect}
        className="flex w-full items-center justify-between rounded-md border border-line px-3 py-2 text-left text-sm hover:border-line-strong"
      >
        <span>{t(`alertLifecycle.type.${alert.type}`)}</span>
        <span className="font-mono text-xs text-ink-muted">{t(`alertLifecycle.status.${alert.status}`)}</span>
      </button>
    </li>
  )
}

function RaiseAlertForm({ onDone }: { onDone: () => void }) {
  const { t } = useTranslation()
  const { authFetch } = useAuth()
  const [type, setType] = useState<AlertType>('RESOURCE_SHORTAGE')
  const [campId, setCampId] = useState('')
  const [description, setDescription] = useState('')

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        void createAlert(authFetch, { type, campId: Number(campId), description }).then(onDone)
      }}
      className="flex flex-col gap-2 rounded-lg border border-line p-3"
    >
      <select value={type} onChange={(event) => setType(event.target.value as AlertType)} className="rounded border border-line p-2 text-sm">
        {ALERT_TYPES.map((option) => (
          <option key={option} value={option}>
            {t(`alertLifecycle.type.${option}`)}
          </option>
        ))}
      </select>
      <input
        value={campId}
        onChange={(event) => setCampId(event.target.value)}
        placeholder={t('alertLifecycle.camp')}
        className="rounded border border-line p-2 text-sm"
      />
      <textarea
        value={description}
        onChange={(event) => setDescription(event.target.value)}
        placeholder={t('alertLifecycle.description')}
        className="rounded border border-line p-2 text-sm"
      />
      <div className="flex gap-2">
        <button type="submit" className="rounded-full border border-line px-3 py-1.5 text-xs">
          {t('alertLifecycle.submit')}
        </button>
        <button type="button" onClick={onDone} className="rounded-full border border-line px-3 py-1.5 text-xs">
          {t('alertLifecycle.cancel')}
        </button>
      </div>
    </form>
  )
}

function AddNoteForm({ onAdd }: { onAdd: (body: string) => void }) {
  const { t } = useTranslation()
  const [body, setBody] = useState('')
  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        if (!body.trim()) return
        onAdd(body)
        setBody('')
      }}
      className="mt-2 flex gap-2"
    >
      <input
        value={body}
        onChange={(event) => setBody(event.target.value)}
        placeholder={t('alertLifecycle.addNote')}
        className="flex-1 rounded border border-line p-2 text-sm"
      />
      <button type="submit" className="rounded-full border border-line px-3 py-1.5 text-xs">
        {t('alertLifecycle.submit')}
      </button>
    </form>
  )
}

function DemoAlertButton() {
  const { t } = useTranslation()
  const { authFetch } = useAuth()
  return (
    <button
      type="button"
      onClick={() => void raiseDemoAlert(authFetch)}
      className="inline-flex h-8 items-center rounded-full border border-line px-3 text-xs text-ink-muted hover:border-line-strong hover:text-ink"
    >
      {t('alertLifecycle.demo')}
    </button>
  )
}
