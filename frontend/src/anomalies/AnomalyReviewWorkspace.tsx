import { useTranslation } from 'react-i18next'
import { useAnomalies, useReviewAnomaly } from '@/anomalies/useAnomalies'
import type { AnomalyFlagView } from '@/anomalies/api'

const DETECTOR_TONE: Record<AnomalyFlagView['detectorType'], string> = {
  ALLOCATION_BURST: 'border-crit/40 bg-crit/10 text-crit',
  DUPLICATE_REGISTRATION: 'border-signal/40 bg-signal/10 text-signal',
  DONATION_PATTERN: 'border-ok/40 bg-ok/10 text-ok',
}

const STATUS_TONE: Record<AnomalyFlagView['status'], string> = {
  OPEN: 'text-ink-muted',
  CONFIRMED: 'text-crit',
  DISMISSED: 'text-ok',
}

/** The anomaly review queue: one card per flag raised by a detector (allocation burst, duplicate
 * registration, donation pattern), each showing the score, summary, and — deliberately set apart
 * from the accusation — the innocent explanation a reviewer should weigh before confirming.
 * Coordinator/Admin-only; gated one level up in OperatorShell. */
export function AnomalyReviewWorkspace() {
  const { t } = useTranslation()
  const anomalies = useAnomalies()
  const review = useReviewAnomaly()

  if (!anomalies) {
    return null
  }

  return (
    <section className="flex flex-col gap-4 p-4 sm:p-6">
      <div>
        <h2 className="text-lg font-semibold text-ink">{t('anomalies.title')}</h2>
        <p className="text-sm text-ink-muted">{t('anomalies.subtitle')}</p>
      </div>
      {anomalies.length === 0 ? (
        <p className="text-sm text-ink-muted">{t('anomalies.empty')}</p>
      ) : (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
          {anomalies.map((flag) => (
            <AnomalyCard
              key={flag.id}
              flag={flag}
              onReview={(toStatus) => review(flag.id, { toStatus })}
            />
          ))}
        </div>
      )}
    </section>
  )
}

function AnomalyCard({
  flag,
  onReview,
}: {
  flag: AnomalyFlagView
  onReview: (toStatus: 'CONFIRMED' | 'DISMISSED') => void
}) {
  const { t, i18n } = useTranslation()
  const detectorLabelKey =
    flag.detectorType === 'ALLOCATION_BURST'
      ? 'anomalies.detectorAllocationBurst'
      : flag.detectorType === 'DUPLICATE_REGISTRATION'
        ? 'anomalies.detectorDuplicateRegistration'
        : 'anomalies.detectorDonationPattern'

  return (
    <div className="rounded-lg border border-line bg-surface p-4">
      <div className="flex items-center justify-between">
        <span
          className={`inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-medium ${DETECTOR_TONE[flag.detectorType]}`}
        >
          {t(detectorLabelKey)}
        </span>
        <span className="font-mono text-sm tabular-nums text-ink">
          {(flag.score * 100).toLocaleString(i18n.language, { maximumFractionDigits: 0 })}%
        </span>
      </div>

      <p className="mt-2 text-sm text-ink">{flag.summary}</p>

      <p className="mt-2 border-l-2 border-line pl-2.5 text-xs text-ink-muted italic">
        <span className="not-italic font-medium">{t('anomalies.innocentExplanationLabel')}: </span>
        {flag.innocentExplanation}
      </p>

      <p className="mt-2 text-xs text-ink-muted">{flag.subjectIds.join(', ')}</p>

      <p className={`mt-2 text-sm font-medium ${STATUS_TONE[flag.status]}`}>
        {t(`anomalies.status${capitalize(flag.status)}`)}
      </p>

      {flag.status === 'OPEN' ? (
        <div className="mt-3 flex items-center gap-2 border-t border-line pt-3">
          <button
            type="button"
            onClick={() => onReview('CONFIRMED')}
            className="inline-flex h-8 items-center rounded-full bg-signal px-4 text-xs font-semibold text-signal-ink transition-opacity hover:opacity-90"
          >
            {t('anomalies.confirm')}
          </button>
          <button
            type="button"
            onClick={() => onReview('DISMISSED')}
            className="inline-flex h-8 items-center rounded-full border border-line px-3 text-xs text-ink-muted transition-colors hover:border-line-strong hover:text-ink"
          >
            {t('anomalies.dismiss')}
          </button>
        </div>
      ) : (
        <p className="mt-3 border-t border-line pt-3 text-xs text-ink-muted">
          {flag.reviewedByUserId !== null && `#${flag.reviewedByUserId} · `}
          {flag.reviewedAt !== null && new Date(flag.reviewedAt).toLocaleString(i18n.language)}
        </p>
      )}
    </div>
  )
}

function capitalize(status: AnomalyFlagView['status']): string {
  return status.charAt(0) + status.slice(1).toLowerCase()
}
