import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { DisasterDrawMap } from '@/world/DisasterDrawMap'
import type { Disaster, DisasterType, GeoJsonPolygon } from '@/world/api'
import { useAdminDisasterList, useCreateDisaster } from '@/admin/useAdminDisasters'
import { DisasterDetailPanel } from '@/admin/DisasterDetailPanel'

const DISASTER_TYPES: DisasterType[] = ['FLOOD', 'CYCLONE']

/**
 * The first Admin-only page in the app: a roster of every registered disaster plus a workbench
 * for declaring a new one or editing a selected one. Sits alongside the shared situation-room
 * chrome (`WorldMap`, the status ribbon), but reads as a control desk rather than a live picture —
 * the roster is a plain ledger (monospace codes, status pills), and the map only appears inside
 * whichever draw/edit action is active, never as ambient decoration.
 */
export function AdminDisasterWorkspace() {
  const { t } = useTranslation()
  const disastersState = useAdminDisasterList()
  const [declaring, setDeclaring] = useState(false)
  const [selectedId, setSelectedId] = useState<number | null>(null)

  if (disastersState.status !== 'ready') return null
  const { disasters } = disastersState
  const selected = disasters.find((d) => d.id === selectedId) ?? null

  return (
    <section className="flex flex-col gap-5 p-4 sm:p-6">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold text-ink">{t('admin.title')}</h2>
          <p className="text-sm text-ink-muted">{t('admin.subtitle')}</p>
        </div>
        {!declaring && (
          <button
            type="button"
            onClick={() => {
              setDeclaring(true)
              setSelectedId(null)
            }}
            className="inline-flex h-9 shrink-0 items-center rounded-full bg-signal px-4 text-xs font-semibold text-signal-ink transition-opacity hover:opacity-90"
          >
            {t('admin.declareCta')}
          </button>
        )}
      </div>

      {declaring && (
        <DeclareDisasterForm disasters={disasters} onDone={() => setDeclaring(false)} />
      )}

      <DisasterRoster
        disasters={disasters}
        selectedId={selectedId}
        onSelect={(id) => {
          setSelectedId((current) => (current === id ? null : id))
          setDeclaring(false)
        }}
      />

      {selected && <DisasterDetailPanel disaster={selected} allDisasters={disasters} />}
    </section>
  )
}

function DisasterRoster({
  disasters,
  selectedId,
  onSelect,
}: {
  disasters: Disaster[]
  selectedId: number | null
  onSelect: (id: number) => void
}) {
  const { t } = useTranslation()

  if (disasters.length === 0) {
    return <p className="text-sm text-ink-muted">{t('admin.empty')}</p>
  }

  return (
    <ul className="flex flex-col divide-y divide-line rounded-xl border border-line bg-surface">
      {disasters.map((disaster) => (
        <li key={disaster.id}>
          <button
            type="button"
            onClick={() => onSelect(disaster.id)}
            className={
              selectedId === disaster.id
                ? 'flex w-full items-center gap-3 bg-surface-2 px-4 py-3 text-left'
                : 'flex w-full items-center gap-3 px-4 py-3 text-left hover:bg-surface-2/60'
            }
          >
            <span
              className={
                disaster.status === 'CLOSED'
                  ? 'h-2 w-2 shrink-0 rounded-full bg-ink-muted'
                  : 'h-2 w-2 shrink-0 rounded-full bg-signal'
              }
              aria-hidden
            />
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-medium text-ink">{disaster.nameEn}</span>
              <span className="block font-mono text-[11px] tracking-wide text-ink-muted uppercase">
                {disaster.code} · {t(`admin.typeValue.${disaster.type}`, disaster.type)}
              </span>
            </span>
            <span className="shrink-0 font-mono text-[10px] tracking-[0.14em] text-ink-muted uppercase">
              {t(`admin.statusValue.${disaster.status}`, disaster.status)}
            </span>
          </button>
        </li>
      ))}
    </ul>
  )
}

function DeclareDisasterForm({ disasters, onDone }: { disasters: Disaster[]; onDone: () => void }) {
  const { t } = useTranslation()
  const createDisaster = useCreateDisaster()
  const [geometry, setGeometry] = useState<GeoJsonPolygon | null>(null)
  const [code, setCode] = useState('')
  const [type, setType] = useState<DisasterType>('FLOOD')
  const [nameEn, setNameEn] = useState('')
  const [nameBn, setNameBn] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [failed, setFailed] = useState(false)

  const canSubmit =
    geometry !== null && code.trim().length > 0 && nameEn.trim().length > 0 && nameBn.trim().length > 0

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!canSubmit || !geometry) return
    setSubmitting(true)
    setFailed(false)
    try {
      await createDisaster({ code: code.trim(), type, nameEn: nameEn.trim(), nameBn: nameBn.trim(), geometry })
      onDone()
    } catch {
      setFailed(true)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="flex flex-col gap-3 rounded-xl border border-line bg-surface p-4 sm:p-5"
    >
      <p className="font-mono text-[11px] tracking-wide text-ink-muted uppercase">{t('admin.declareTitle')}</p>

      <div className="h-72 overflow-hidden rounded-lg border border-line">
        <DisasterDrawMap disasters={disasters} mode="polygon" onPolygonDrawn={setGeometry} onPointPlaced={() => {}} />
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <label className="block text-xs font-medium text-ink-muted">
          {t('admin.code')}
          <input
            type="text"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            className="mt-1 block h-9 w-full rounded-lg border border-line bg-bg px-2.5 text-sm text-ink outline-none focus-visible:border-signal focus-visible:ring-2 focus-visible:ring-signal/40"
          />
        </label>
        <label className="block text-xs font-medium text-ink-muted">
          {t('admin.type')}
          <select
            value={type}
            onChange={(e) => setType(e.target.value as DisasterType)}
            className="mt-1 block h-9 w-full rounded-lg border border-line bg-bg px-2.5 text-sm text-ink outline-none focus-visible:border-signal"
          >
            {DISASTER_TYPES.map((value) => (
              <option key={value} value={value}>
                {t(`admin.typeValue.${value}`)}
              </option>
            ))}
          </select>
        </label>
        <label className="block text-xs font-medium text-ink-muted">
          {t('admin.nameEn')}
          <input
            type="text"
            value={nameEn}
            onChange={(e) => setNameEn(e.target.value)}
            className="mt-1 block h-9 w-full rounded-lg border border-line bg-bg px-2.5 text-sm text-ink outline-none focus-visible:border-signal focus-visible:ring-2 focus-visible:ring-signal/40"
          />
        </label>
        <label className="block text-xs font-medium text-ink-muted">
          {t('admin.nameBn')}
          <input
            type="text"
            value={nameBn}
            onChange={(e) => setNameBn(e.target.value)}
            className="mt-1 block h-9 w-full rounded-lg border border-line bg-bg px-2.5 text-sm text-ink outline-none focus-visible:border-signal focus-visible:ring-2 focus-visible:ring-signal/40"
          />
        </label>
      </div>

      {failed && <p className="text-sm text-crit">{t('admin.error')}</p>}

      <div className="flex items-center gap-2">
        <button
          type="submit"
          disabled={!canSubmit || submitting}
          className="inline-flex h-9 items-center rounded-full bg-signal px-4 text-xs font-semibold text-signal-ink transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {submitting ? t('admin.submitting') : t('admin.declareSubmit')}
        </button>
        <button
          type="button"
          onClick={onDone}
          className="inline-flex h-9 items-center rounded-full px-3 text-xs text-ink-muted hover:text-ink"
        >
          {t('admin.cancel')}
        </button>
      </div>
    </form>
  )
}
