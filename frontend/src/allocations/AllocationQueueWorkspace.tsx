import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAllocations, useTransitionAllocation } from '@/allocations/useAllocations'
import type { AllocationSummary } from '@/allocations/api'

const STATUS_TONE: Record<AllocationSummary['status'], string> = {
  RECOMMENDED: 'text-ink-muted',
  APPROVED: 'text-ok',
  MODIFIED: 'text-ok',
  REJECTED: 'text-crit',
}

/** The five sub-scores behind `priorityScore`, in the same order the backend combines them
 * (`AllocationScoringService.priorityScore`) with their fixed weights — shown as proportional
 * bars (the same meter convention as `MyCampPanel`'s occupancy bar) so a coordinator can see at a
 * glance *why* one recommendation outranks another, not just read five bare numbers. */
const FACTORS: { key: keyof AllocationSummary; labelKey: string; weight: number }[] = [
  { key: 'shortageScore', labelKey: 'allocations.factorShortage', weight: 0.35 },
  { key: 'severityScore', labelKey: 'allocations.factorSeverity', weight: 0.3 },
  { key: 'fairnessScore', labelKey: 'allocations.factorFairness', weight: 0.2 },
  { key: 'populationScore', labelKey: 'allocations.factorPopulation', weight: 0.15 },
]

/** The Recommended Allocations decision queue: one card per recommendation, showing the
 * per-factor priority breakdown a coordinator needs to defend the ranking, with Approve / Modify
 * / Reject actions. Camp Manager sees the same list (scoped server-side to their own camps) but
 * read-only — no action buttons — per the ticket spec's item 49. */
export function AllocationQueueWorkspace({ apiRole }: { apiRole: string }) {
  const { t } = useTranslation()
  const allocations = useAllocations()
  const transition = useTransitionAllocation()

  if (!allocations) {
    return null
  }

  const isCampManager = apiRole === 'CAMP_MANAGER'

  return (
    <section className="flex flex-col gap-4 p-4 sm:p-6">
      <div>
        <h2 className="text-lg font-semibold text-ink">{t('allocations.title')}</h2>
        <p className="text-sm text-ink-muted">
          {isCampManager ? t('allocations.subtitleCampManager') : t('allocations.subtitle')}
        </p>
      </div>
      {allocations.length === 0 ? (
        <p className="text-sm text-ink-muted">{t('allocations.empty')}</p>
      ) : (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
          {allocations.map((a) => (
            <AllocationCard
              key={a.id}
              allocation={a}
              onTransition={(toStatus, quantity) => transition(a.id, { toStatus, quantity })}
            />
          ))}
        </div>
      )}
    </section>
  )
}

function AllocationCard({
  allocation,
  onTransition,
}: {
  allocation: AllocationSummary
  onTransition: (toStatus: 'APPROVED' | 'MODIFIED' | 'REJECTED', quantity?: number) => void
}) {
  const { t, i18n } = useTranslation()
  const resourceLabel = t(`camp.resource.${allocation.resourceType}`)
  const isPending = allocation.status === 'RECOMMENDED'

  return (
    <div className="rounded-lg border border-line bg-surface p-4">
      <div className="flex items-center justify-between">
        <p className="text-sm font-medium text-ink">
          {t('allocations.route', { source: allocation.sourceCampId, target: allocation.targetCampId })}
        </p>
        <span className="text-xs text-ink-muted">{resourceLabel}</span>
      </div>

      <p className="mt-2 text-sm text-ink">
        {t('allocations.recommendedQuantity')}:{' '}
        <span className="tabular-nums">{allocation.recommendedQuantity.toLocaleString(i18n.language)}</span>
      </p>
      {allocation.decidedQuantity !== null && (
        <p className="text-sm text-ink">
          {t('allocations.decidedQuantity')}:{' '}
          <span className="tabular-nums">{allocation.decidedQuantity.toLocaleString(i18n.language)}</span>
        </p>
      )}
      <p className={`text-sm font-medium ${STATUS_TONE[allocation.status]}`}>
        {t(`allocations.statusValue.${allocation.status}`)}
      </p>

      <PriorityBreakdown allocation={allocation} />

      {allocation.canAct && isPending && <DecisionActions onTransition={onTransition} />}
    </div>
  )
}

/** The explainability panel: an overall priority meter, then each weighted sub-score as its own
 * proportional bar, ordered by weight (heaviest factor first) so the biggest driver of the ranking
 * reads first. */
function PriorityBreakdown({ allocation }: { allocation: AllocationSummary }) {
  const { t } = useTranslation()

  return (
    <div className="mt-3 border-t border-line pt-3">
      <div className="flex items-baseline justify-between">
        <span className="text-xs font-semibold tracking-wide text-ink-muted uppercase">
          {t('allocations.priorityScore')}
        </span>
        <span className="font-mono text-sm tabular-nums text-ink">{allocation.priorityScore.toFixed(2)}</span>
      </div>
      <Meter value={allocation.priorityScore} tone="bg-signal" trackClassName="h-1.5" />

      <ul className="mt-3 flex flex-col gap-1.5">
        {FACTORS.map((factor) => {
          const value = allocation[factor.key] as number
          return (
            <li key={factor.labelKey} className="flex items-center gap-2">
              <span className="w-28 shrink-0 text-[11px] text-ink-muted">{t(factor.labelKey)}</span>
              <Meter value={value} tone="bg-ink-muted" trackClassName="h-1" />
              <span className="w-9 shrink-0 text-right font-mono text-[11px] tabular-nums text-ink-muted">
                {value.toFixed(2)}
              </span>
            </li>
          )
        })}
      </ul>
    </div>
  )
}

function Meter({ value, tone, trackClassName }: { value: number; tone: string; trackClassName: string }) {
  const pct = Math.min(100, Math.max(0, value * 100))
  return (
    <div className={`mt-1 w-full overflow-hidden rounded-full bg-surface-2 ${trackClassName}`}>
      <div className={`h-full rounded-full transition-[width] duration-500 ease-out ${tone}`} style={{ width: `${pct}%` }} />
    </div>
  )
}

/** Approve / Modify / Reject, given deliberately unequal visual weight — mirrors this product's
 * existing "primary filled action" convention (see the sign-in / registration submit buttons)
 * rather than three identical buttons in a row:
 *  - Approve is the filled, one-click default path (accept the recommendation as-is).
 *  - Modify pairs a quantity input with its own button — a visibly different, two-step
 *    interaction, disabled until a positive quantity is entered.
 *  - Reject is a quiet outlined action that only turns critical-red on hover/focus and requires a
 *    second, explicit confirm click — since every allocation decision is terminal (no undo), it
 *    gets a light confirmation step the other two, which can be corrected with a fresh
 *    recommendation, don't need. */
function DecisionActions({
  onTransition,
}: {
  onTransition: (toStatus: 'APPROVED' | 'MODIFIED' | 'REJECTED', quantity?: number) => void
}) {
  const { t } = useTranslation()
  const [modifyQuantity, setModifyQuantity] = useState('')
  const [confirmingReject, setConfirmingReject] = useState(false)
  const parsedQuantity = Number(modifyQuantity)
  const canModify = modifyQuantity.trim() !== '' && Number.isFinite(parsedQuantity) && parsedQuantity > 0

  return (
    <div className="mt-3 flex flex-col gap-2 border-t border-line pt-3">
      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={() => onTransition('APPROVED')}
          className="inline-flex h-8 items-center rounded-full bg-signal px-4 text-xs font-semibold text-signal-ink transition-opacity hover:opacity-90"
        >
          {t('allocations.approve')}
        </button>

        <div className="inline-flex h-8 items-center overflow-hidden rounded-full border border-line">
          <input
            type="number"
            min="0"
            step="any"
            value={modifyQuantity}
            onChange={(e) => setModifyQuantity(e.target.value)}
            placeholder={t('allocations.modifyPlaceholder')}
            aria-label={t('allocations.modifyPlaceholder')}
            className="h-full w-20 bg-transparent px-2.5 text-xs text-ink outline-none"
          />
          <button
            type="button"
            disabled={!canModify}
            onClick={() => onTransition('MODIFIED', parsedQuantity)}
            className="h-full border-l border-line px-3 text-xs text-ink-muted transition-colors hover:enabled:text-ink disabled:cursor-not-allowed disabled:opacity-40"
          >
            {t('allocations.modify')}
          </button>
        </div>

        {confirmingReject ? (
          <span className="ml-auto inline-flex items-center gap-1.5">
            <button
              type="button"
              onClick={() => onTransition('REJECTED')}
              className="inline-flex h-8 items-center rounded-full border border-crit px-3 text-xs font-medium text-crit"
            >
              {t('allocations.rejectConfirm')}
            </button>
            <button
              type="button"
              onClick={() => setConfirmingReject(false)}
              className="inline-flex h-8 items-center rounded-full px-2 text-xs text-ink-muted hover:text-ink"
            >
              {t('allocations.cancel')}
            </button>
          </span>
        ) : (
          <button
            type="button"
            onClick={() => setConfirmingReject(true)}
            className="ml-auto inline-flex h-8 items-center rounded-full border border-line px-3 text-xs text-ink-muted transition-colors hover:border-crit hover:text-crit"
          >
            {t('allocations.reject')}
          </button>
        )}
      </div>
    </div>
  )
}
