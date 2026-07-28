import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { DisasterDrawMap, type DrawMode } from '@/world/DisasterDrawMap'
import type { Disaster, DisasterType, GeoJsonPolygon } from '@/world/api'
import { useDisasters } from '@/world/useDisasters'
import { useSubmitProposal } from '@/proposals/useProposals'
import type { ProposalType } from '@/proposals/api'

const PROPOSAL_TYPES: ProposalType[] = [
  'DISASTER_CREATE',
  'DISASTER_UPDATE',
  'DISASTER_CLOSE',
  'AFFECTED_AREA_CREATE',
  'CAMP_CREATE',
]

const DISASTER_TYPES: DisasterType[] = ['FLOOD', 'CYCLONE']

const MAP_MODE: Record<ProposalType, DrawMode | null> = {
  DISASTER_CREATE: 'polygon',
  DISASTER_UPDATE: 'polygon',
  DISASTER_CLOSE: null,
  AFFECTED_AREA_CREATE: 'polygon',
  CAMP_CREATE: 'point',
}

/**
 * The Coordinator's "propose a change" tab. Lighter than the Admin's declare/edit workbench by
 * design: a coordinator proposes exactly one thing at a time and then sees it land in the
 * queue — there is no roster, no detail panel, no geometry history here, because none of that
 * is this role's job (the Central Authority owns the decision; this page only files the ask).
 * Picking a type swaps in the one map mode and field set that type needs; submitting resets back
 * to a blank form with a short-lived confirmation rather than staying on a "success" screen.
 */
export function CoordinatorProposeWorkspace() {
  const { t } = useTranslation()
  const disastersState = useDisasters()
  const [type, setType] = useState<ProposalType>('DISASTER_CREATE')
  const [confirmedAt, setConfirmedAt] = useState<number | null>(null)

  if (disastersState.status !== 'ready') return null
  const { disasters } = disastersState

  return (
    <section className="flex flex-col gap-5 p-4 sm:p-6">
      <div>
        <h2 className="text-lg font-semibold text-ink">{t('proposals.proposeTitle')}</h2>
        <p className="text-sm text-ink-muted">{t('proposals.proposeSubtitle')}</p>
      </div>

      <div className="flex flex-wrap gap-2">
        {PROPOSAL_TYPES.map((value) => (
          <button
            key={value}
            type="button"
            onClick={() => {
              setType(value)
              setConfirmedAt(null)
            }}
            className={
              type === value
                ? 'inline-flex h-8 items-center rounded-full bg-signal px-3.5 text-xs font-semibold text-signal-ink'
                : 'inline-flex h-8 items-center rounded-full border border-line px-3.5 text-xs text-ink-muted hover:text-ink'
            }
          >
            {t(`proposals.type.${value}`)}
          </button>
        ))}
      </div>

      {confirmedAt !== null && (
        <p className="text-sm text-ok" key={confirmedAt}>
          {t('proposals.submitted')}
        </p>
      )}

      <ProposeForm
        key={type}
        type={type}
        disasters={disasters}
        onSubmitted={() => setConfirmedAt(Date.now())}
      />
    </section>
  )
}

function ProposeForm({
  type,
  disasters,
  onSubmitted,
}: {
  type: ProposalType
  disasters: Disaster[]
  onSubmitted: () => void
}) {
  const { t } = useTranslation()
  const submitProposal = useSubmitProposal()

  const [targetDisasterId, setTargetDisasterId] = useState<number | null>(null)
  const [geometry, setGeometry] = useState<GeoJsonPolygon | null>(null)
  const [point, setPoint] = useState<{ lat: number; lng: number } | null>(null)
  const [code, setCode] = useState('')
  const [disasterType, setDisasterType] = useState<DisasterType>('FLOOD')
  const [nameEn, setNameEn] = useState('')
  const [nameBn, setNameBn] = useState('')
  const [capacity, setCapacity] = useState('')
  const [initialPopulation, setInitialPopulation] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [failed, setFailed] = useState(false)

  const needsExistingDisaster = type !== 'DISASTER_CREATE'
  const mapMode = MAP_MODE[type]

  const canSubmit = (() => {
    if (needsExistingDisaster && targetDisasterId === null) return false
    switch (type) {
      case 'DISASTER_CREATE':
        return geometry !== null && code.trim() !== '' && nameEn.trim() !== '' && nameBn.trim() !== ''
      case 'DISASTER_UPDATE':
        return geometry !== null || nameEn.trim() !== '' || nameBn.trim() !== ''
      case 'DISASTER_CLOSE':
        return true
      case 'AFFECTED_AREA_CREATE':
        return geometry !== null && nameEn.trim() !== '' && nameBn.trim() !== ''
      case 'CAMP_CREATE':
        return (
          point !== null &&
          code.trim() !== '' &&
          nameEn.trim() !== '' &&
          nameBn.trim() !== '' &&
          capacity.trim() !== '' &&
          initialPopulation.trim() !== ''
        )
    }
  })()

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!canSubmit) return
    setSubmitting(true)
    setFailed(false)
    try {
      switch (type) {
        case 'DISASTER_CREATE':
          await submitProposal({
            proposalType: 'DISASTER_CREATE',
            targetDisasterId: null,
            payload: {
              code: code.trim(),
              type: disasterType,
              nameEn: nameEn.trim(),
              nameBn: nameBn.trim(),
              geometry: geometry as GeoJsonPolygon,
            },
          })
          break
        case 'DISASTER_UPDATE':
          await submitProposal({
            proposalType: 'DISASTER_UPDATE',
            targetDisasterId: targetDisasterId as number,
            payload: {
              ...(nameEn.trim() !== '' ? { nameEn: nameEn.trim() } : {}),
              ...(nameBn.trim() !== '' ? { nameBn: nameBn.trim() } : {}),
              ...(geometry ? { geometry } : {}),
            },
          })
          break
        case 'DISASTER_CLOSE':
          await submitProposal({
            proposalType: 'DISASTER_CLOSE',
            targetDisasterId: targetDisasterId as number,
            payload: {},
          })
          break
        case 'AFFECTED_AREA_CREATE':
          await submitProposal({
            proposalType: 'AFFECTED_AREA_CREATE',
            targetDisasterId: targetDisasterId as number,
            payload: { nameEn: nameEn.trim(), nameBn: nameBn.trim(), geometry: geometry as GeoJsonPolygon },
          })
          break
        case 'CAMP_CREATE':
          await submitProposal({
            proposalType: 'CAMP_CREATE',
            targetDisasterId: targetDisasterId as number,
            payload: {
              code: code.trim(),
              nameEn: nameEn.trim(),
              nameBn: nameBn.trim(),
              lat: (point as { lat: number; lng: number }).lat,
              lng: (point as { lat: number; lng: number }).lng,
              capacity: Number(capacity),
              initialPopulation: Number(initialPopulation),
            },
          })
          break
      }
      setCode('')
      setNameEn('')
      setNameBn('')
      setCapacity('')
      setInitialPopulation('')
      setGeometry(null)
      setPoint(null)
      onSubmitted()
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
      {needsExistingDisaster && (
        <label className="block text-xs font-medium text-ink-muted">
          {t('proposals.targetDisaster')}
          <select
            value={targetDisasterId ?? ''}
            onChange={(e) => setTargetDisasterId(e.target.value === '' ? null : Number(e.target.value))}
            className="mt-1 block h-9 w-full rounded-lg border border-line bg-bg px-2.5 text-sm text-ink outline-none focus-visible:border-signal"
          >
            <option value="">{t('proposals.targetDisasterPlaceholder')}</option>
            {disasters.map((d) => (
              <option key={d.id} value={d.id}>
                {d.nameEn}
              </option>
            ))}
          </select>
        </label>
      )}

      {mapMode && (
        <div className="h-64 overflow-hidden rounded-lg border border-line">
          <DisasterDrawMap
            disasters={disasters}
            mode={mapMode}
            onPolygonDrawn={setGeometry}
            onPointPlaced={(lat, lng) => setPoint({ lat, lng })}
          />
        </div>
      )}

      {(type === 'DISASTER_CREATE' || type === 'CAMP_CREATE') && (
        <label className="block text-xs font-medium text-ink-muted">
          {type === 'CAMP_CREATE' ? t('admin.detail.campCode') : t('admin.code')}
          <input
            type="text"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            className="mt-1 block h-9 w-full rounded-lg border border-line bg-bg px-2.5 text-sm text-ink outline-none focus-visible:border-signal focus-visible:ring-2 focus-visible:ring-signal/40"
          />
        </label>
      )}

      {type === 'DISASTER_CREATE' && (
        <label className="block text-xs font-medium text-ink-muted">
          {t('admin.type')}
          <select
            value={disasterType}
            onChange={(e) => setDisasterType(e.target.value as DisasterType)}
            className="mt-1 block h-9 w-full rounded-lg border border-line bg-bg px-2.5 text-sm text-ink outline-none focus-visible:border-signal"
          >
            {DISASTER_TYPES.map((value) => (
              <option key={value} value={value}>
                {t(`admin.typeValue.${value}`)}
              </option>
            ))}
          </select>
        </label>
      )}

      {type !== 'DISASTER_CLOSE' && (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
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
      )}

      {type === 'CAMP_CREATE' && (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <label className="block text-xs font-medium text-ink-muted">
            {t('admin.detail.campCapacity')}
            <input
              type="number"
              min="0"
              value={capacity}
              onChange={(e) => setCapacity(e.target.value)}
              className="mt-1 block h-9 w-full rounded-lg border border-line bg-bg px-2.5 text-sm text-ink outline-none focus-visible:border-signal focus-visible:ring-2 focus-visible:ring-signal/40"
            />
          </label>
          <label className="block text-xs font-medium text-ink-muted">
            {t('admin.detail.campInitialPopulation')}
            <input
              type="number"
              min="0"
              value={initialPopulation}
              onChange={(e) => setInitialPopulation(e.target.value)}
              className="mt-1 block h-9 w-full rounded-lg border border-line bg-bg px-2.5 text-sm text-ink outline-none focus-visible:border-signal focus-visible:ring-2 focus-visible:ring-signal/40"
            />
          </label>
        </div>
      )}

      {failed && <p className="text-sm text-crit">{t('proposals.error')}</p>}

      <div>
        <button
          type="submit"
          disabled={!canSubmit || submitting}
          className="inline-flex h-9 items-center rounded-full bg-signal px-4 text-xs font-semibold text-signal-ink transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {submitting ? t('proposals.submitting') : t('proposals.submit')}
        </button>
      </div>
    </form>
  )
}
