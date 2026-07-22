import { useTranslation } from 'react-i18next'

/**
 * The signature element for arrival: two independent stamps, not a progress bar. Dual-source
 * confirmation is a real-world act (a person taps, then a manager separately checks off the
 * same person) — a single bar would flatten that into one fact when it is actually two.
 */
export function ArrivalStamps({
  representativeArrived,
  managerConfirmedArrived,
}: {
  representativeArrived: boolean
  managerConfirmedArrived: boolean
}) {
  const { t } = useTranslation()
  return (
    <div className="flex items-center gap-2" role="group" aria-label={t('family.arrival.stampsLabel')}>
      <Stamp done={representativeArrived} label={t('family.arrival.repStamp')} />
      <span className={`h-px w-5 ${representativeArrived ? 'bg-signal' : 'bg-line'}`} aria-hidden />
      <Stamp done={managerConfirmedArrived} label={t('family.arrival.managerStamp')} />
    </div>
  )
}

function Stamp({ done, label }: { done: boolean; label: string }) {
  return (
    <div className="flex flex-col items-center gap-1">
      <span
        className={`flex h-7 w-7 items-center justify-center rounded-full border-2 text-xs font-bold transition-colors duration-300 ${
          done ? 'border-signal bg-signal text-signal-ink' : 'border-line text-ink-muted'
        }`}
        aria-hidden
      >
        {done ? '✓' : ''}
      </span>
      <span className="text-[11px] text-ink-muted">{label}</span>
    </div>
  )
}
