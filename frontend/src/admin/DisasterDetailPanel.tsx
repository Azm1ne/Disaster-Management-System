import { useState, type FormEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { DisasterDrawMap } from '@/world/DisasterDrawMap'
import type { Disaster, GeoJsonPolygon } from '@/world/api'
import { GeometryHistoryList } from '@/admin/GeometryHistoryList'
import {
  useCloseDisaster,
  useCreateAffectedArea,
  useCreateCamp,
  useUpdateDisaster,
} from '@/admin/useAdminDisasters'

type SubMode = 'view' | 'boundary' | 'area' | 'camp' | 'history'

/**
 * One selected disaster's workbench: redraw its boundary, place a new affected area or camp
 * under it, close it, or read its geometry history. Exactly one of these is active at a time —
 * a segmented control picks the sub-mode, and `DisasterDrawMap` is keyed by sub-mode so switching
 * always mounts a fresh map (no stale leaflet-draw handler carried over from the previous tool).
 */
export function DisasterDetailPanel({ disaster, allDisasters }: { disaster: Disaster; allDisasters: Disaster[] }) {
  const { t } = useTranslation()
  const [subMode, setSubMode] = useState<SubMode>('view')
  const [historySubject, setHistorySubject] = useState<{ type: 'DISASTER' | 'AFFECTED_AREA'; id: number }>({
    type: 'DISASTER',
    id: disaster.id,
  })

  const isClosed = disaster.status === 'CLOSED'

  return (
    <div className="flex flex-col gap-4 rounded-xl border border-line bg-surface p-4 sm:p-5">
      <div>
        <p className="font-mono text-[11px] tracking-wide text-ink-muted uppercase">{disaster.code}</p>
        <h3 className="text-base font-semibold text-ink">{disaster.nameEn}</h3>
      </div>

      <div className="flex flex-wrap gap-1.5 border-b border-line pb-4">
        <TabButton active={subMode === 'view'} onClick={() => setSubMode('view')}>
          {t('admin.detail.view')}
        </TabButton>
        {!isClosed && (
          <>
            <TabButton active={subMode === 'boundary'} onClick={() => setSubMode('boundary')}>
              {t('admin.detail.boundary')}
            </TabButton>
            <TabButton active={subMode === 'area'} onClick={() => setSubMode('area')}>
              {t('admin.detail.addArea')}
            </TabButton>
            <TabButton active={subMode === 'camp'} onClick={() => setSubMode('camp')}>
              {t('admin.detail.addCamp')}
            </TabButton>
          </>
        )}
        <TabButton active={subMode === 'history'} onClick={() => setSubMode('history')}>
          {t('admin.detail.history')}
        </TabButton>
      </div>

      {subMode === 'view' && <ViewSummary disaster={disaster} />}
      {subMode === 'boundary' && (
        <EditBoundaryForm key={`boundary-${disaster.id}`} disaster={disaster} allDisasters={allDisasters} />
      )}
      {subMode === 'area' && (
        <AddAreaForm key={`area-${disaster.id}`} disaster={disaster} allDisasters={allDisasters} />
      )}
      {subMode === 'camp' && (
        <AddCampForm key={`camp-${disaster.id}`} disaster={disaster} allDisasters={allDisasters} />
      )}
      {subMode === 'history' && (
        <div className="flex flex-col gap-3">
          <label className="text-xs font-medium text-ink-muted">
            {t('admin.history.subject')}
            <select
              value={`${historySubject.type}:${historySubject.id}`}
              onChange={(e) => {
                const [type, id] = e.target.value.split(':')
                setHistorySubject({ type: type as 'DISASTER' | 'AFFECTED_AREA', id: Number(id) })
              }}
              className="mt-1 block h-9 w-full max-w-xs rounded-lg border border-line bg-bg px-2.5 text-sm text-ink outline-none focus-visible:border-signal"
            >
              <option value={`DISASTER:${disaster.id}`}>{t('admin.history.subjectDisaster')}</option>
              {disaster.affectedAreas.map((area) => (
                <option key={area.id} value={`AFFECTED_AREA:${area.id}`}>
                  {t('admin.history.subjectArea', { name: area.nameEn })}
                </option>
              ))}
            </select>
          </label>
          <GeometryHistoryList
            key={`${historySubject.type}-${historySubject.id}`}
            subjectType={historySubject.type}
            subjectId={historySubject.id}
          />
        </div>
      )}
    </div>
  )
}

function TabButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={
        active
          ? 'rounded-full bg-surface-2 px-3 py-1.5 text-xs font-semibold text-ink'
          : 'rounded-full px-3 py-1.5 text-xs text-ink-muted hover:text-ink'
      }
    >
      {children}
    </button>
  )
}

function ViewSummary({ disaster }: { disaster: Disaster }) {
  const { t } = useTranslation()
  const close = useCloseDisaster()
  const [confirming, setConfirming] = useState(false)
  const [closing, setClosing] = useState(false)

  return (
    <div className="flex flex-col gap-3">
      <dl className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
        <Stat label={t('admin.type')} value={t(`admin.typeValue.${disaster.type}`, disaster.type)} />
        <Stat label={t('admin.statusLabel')} value={t(`admin.statusValue.${disaster.status}`, disaster.status)} />
        <Stat label={t('admin.detail.areasCount')} value={String(disaster.affectedAreas.length)} />
        <Stat label={t('admin.detail.campsCount')} value={String(disaster.camps.length)} />
      </dl>

      {disaster.status !== 'CLOSED' && (
        <div className="flex items-center gap-2 border-t border-line pt-3">
          {confirming ? (
            <>
              <button
                type="button"
                disabled={closing}
                onClick={async () => {
                  setClosing(true)
                  await close(disaster.id)
                  setClosing(false)
                  setConfirming(false)
                }}
                className="inline-flex h-8 items-center rounded-full border border-crit px-3 text-xs font-medium text-crit disabled:opacity-60"
              >
                {t('admin.closeConfirm')}
              </button>
              <button
                type="button"
                onClick={() => setConfirming(false)}
                className="inline-flex h-8 items-center rounded-full px-2 text-xs text-ink-muted hover:text-ink"
              >
                {t('admin.cancel')}
              </button>
            </>
          ) : (
            <button
              type="button"
              onClick={() => setConfirming(true)}
              className="inline-flex h-8 items-center rounded-full border border-line px-3 text-xs text-ink-muted transition-colors hover:border-crit hover:text-crit"
            >
              {t('admin.close')}
            </button>
          )}
        </div>
      )}
    </div>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="font-mono text-[10px] tracking-[0.14em] text-ink-muted uppercase">{label}</dt>
      <dd className="mt-0.5 text-sm text-ink">{value}</dd>
    </div>
  )
}

function EditBoundaryForm({ disaster, allDisasters }: { disaster: Disaster; allDisasters: Disaster[] }) {
  const { t } = useTranslation()
  const updateDisaster = useUpdateDisaster()
  const [geometry, setGeometry] = useState<GeoJsonPolygon | null>(null)
  const [nameEn, setNameEn] = useState(disaster.nameEn)
  const [nameBn, setNameBn] = useState(disaster.nameBn)
  const [submitting, setSubmitting] = useState(false)
  const [failed, setFailed] = useState(false)

  const nameEnChanged = nameEn.trim() !== disaster.nameEn && nameEn.trim().length > 0
  const nameBnChanged = nameBn.trim() !== disaster.nameBn && nameBn.trim().length > 0
  const canSubmit = geometry !== null || nameEnChanged || nameBnChanged

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!canSubmit) return
    setSubmitting(true)
    setFailed(false)
    try {
      await updateDisaster(disaster.id, {
        ...(nameEnChanged ? { nameEn: nameEn.trim() } : {}),
        ...(nameBnChanged ? { nameBn: nameBn.trim() } : {}),
        ...(geometry ? { geometry } : {}),
      })
    } catch {
      setFailed(true)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-3">
      <div className="h-72 overflow-hidden rounded-lg border border-line">
        <DisasterDrawMap
          disasters={allDisasters}
          mode="polygon"
          editingGeometry={geometry ?? disaster.geometry}
          onPolygonDrawn={setGeometry}
          onPointPlaced={() => {}}
        />
      </div>
      <NameFields nameEn={nameEn} setNameEn={setNameEn} nameBn={nameBn} setNameBn={setNameBn} />
      {failed && <p className="text-sm text-crit">{t('admin.error')}</p>}
      <SubmitButton disabled={!canSubmit} submitting={submitting} label={t('admin.detail.boundarySubmit')} />
    </form>
  )
}

function AddAreaForm({ disaster, allDisasters }: { disaster: Disaster; allDisasters: Disaster[] }) {
  const { t } = useTranslation()
  const createAffectedArea = useCreateAffectedArea()
  const [geometry, setGeometry] = useState<GeoJsonPolygon | null>(null)
  const [nameEn, setNameEn] = useState('')
  const [nameBn, setNameBn] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [failed, setFailed] = useState(false)

  const canSubmit = geometry !== null && nameEn.trim().length > 0 && nameBn.trim().length > 0

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!canSubmit || !geometry) return
    setSubmitting(true)
    setFailed(false)
    try {
      await createAffectedArea(disaster.id, { nameEn: nameEn.trim(), nameBn: nameBn.trim(), geometry })
      setGeometry(null)
      setNameEn('')
      setNameBn('')
    } catch {
      setFailed(true)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-3">
      <div className="h-72 overflow-hidden rounded-lg border border-line">
        <DisasterDrawMap
          disasters={allDisasters}
          mode="polygon"
          onPolygonDrawn={setGeometry}
          onPointPlaced={() => {}}
        />
      </div>
      <NameFields nameEn={nameEn} setNameEn={setNameEn} nameBn={nameBn} setNameBn={setNameBn} />
      {failed && <p className="text-sm text-crit">{t('admin.error')}</p>}
      <SubmitButton disabled={!canSubmit} submitting={submitting} label={t('admin.detail.areaSubmit')} />
    </form>
  )
}

function AddCampForm({ disaster, allDisasters }: { disaster: Disaster; allDisasters: Disaster[] }) {
  const { t } = useTranslation()
  const createCamp = useCreateCamp()
  const [point, setPoint] = useState<{ lat: number; lng: number } | null>(null)
  const [code, setCode] = useState('')
  const [nameEn, setNameEn] = useState('')
  const [nameBn, setNameBn] = useState('')
  const [capacity, setCapacity] = useState('')
  const [initialPopulation, setInitialPopulation] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [failed, setFailed] = useState(false)

  const parsedCapacity = Number(capacity)
  const parsedPopulation = Number(initialPopulation)
  const canSubmit =
    point !== null &&
    code.trim().length > 0 &&
    nameEn.trim().length > 0 &&
    nameBn.trim().length > 0 &&
    Number.isFinite(parsedCapacity) &&
    parsedCapacity >= 0 &&
    Number.isFinite(parsedPopulation) &&
    parsedPopulation >= 0

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!canSubmit || !point) return
    setSubmitting(true)
    setFailed(false)
    try {
      await createCamp(disaster.id, {
        code: code.trim(),
        nameEn: nameEn.trim(),
        nameBn: nameBn.trim(),
        lat: point.lat,
        lng: point.lng,
        capacity: parsedCapacity,
        initialPopulation: parsedPopulation,
      })
      setPoint(null)
      setCode('')
      setNameEn('')
      setNameBn('')
      setCapacity('')
      setInitialPopulation('')
    } catch {
      setFailed(true)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-3">
      <div className="h-72 overflow-hidden rounded-lg border border-line">
        <DisasterDrawMap
          disasters={allDisasters}
          mode="point"
          onPolygonDrawn={() => {}}
          onPointPlaced={(lat, lng) => setPoint({ lat, lng })}
        />
      </div>
      <TextField label={t('admin.detail.campCode')} value={code} onChange={setCode} />
      <NameFields nameEn={nameEn} setNameEn={setNameEn} nameBn={nameBn} setNameBn={setNameBn} />
      <div className="grid grid-cols-2 gap-3">
        <NumberField label={t('admin.detail.campCapacity')} value={capacity} onChange={setCapacity} />
        <NumberField label={t('admin.detail.campInitialPopulation')} value={initialPopulation} onChange={setInitialPopulation} />
      </div>
      {failed && <p className="text-sm text-crit">{t('admin.error')}</p>}
      <SubmitButton disabled={!canSubmit} submitting={submitting} label={t('admin.detail.campSubmit')} />
    </form>
  )
}

function NameFields({
  nameEn,
  setNameEn,
  nameBn,
  setNameBn,
}: {
  nameEn: string
  setNameEn: (v: string) => void
  nameBn: string
  setNameBn: (v: string) => void
}) {
  const { t } = useTranslation()
  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
      <TextField label={t('admin.nameEn')} value={nameEn} onChange={setNameEn} />
      <TextField label={t('admin.nameBn')} value={nameBn} onChange={setNameBn} />
    </div>
  )
}

function TextField({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <label className="block text-xs font-medium text-ink-muted">
      {label}
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="mt-1 block h-9 w-full rounded-lg border border-line bg-bg px-2.5 text-sm text-ink outline-none focus-visible:border-signal focus-visible:ring-2 focus-visible:ring-signal/40"
      />
    </label>
  )
}

function NumberField({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <label className="block text-xs font-medium text-ink-muted">
      {label}
      <input
        type="number"
        min="0"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="mt-1 block h-9 w-full rounded-lg border border-line bg-bg px-2.5 text-sm text-ink outline-none focus-visible:border-signal focus-visible:ring-2 focus-visible:ring-signal/40"
      />
    </label>
  )
}

function SubmitButton({ disabled, submitting, label }: { disabled: boolean; submitting: boolean; label: string }) {
  const { t } = useTranslation()
  return (
    <button
      type="submit"
      disabled={disabled || submitting}
      className="inline-flex h-9 items-center self-start rounded-full bg-signal px-4 text-xs font-semibold text-signal-ink transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-40"
    >
      {submitting ? t('admin.submitting') : label}
    </button>
  )
}
