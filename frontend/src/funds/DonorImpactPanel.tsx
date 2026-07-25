import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useDisasters } from '@/world/useDisasters'
import { useDonate, useMyDonations, useMyImpact } from '@/funds/useFunds'
import type { DisasterImpact } from '@/funds/api'

/**
 * The Donor's own view: give to a disaster, then watch the same gift as an unbroken chain —
 * Donation → Fund → Camp — with nothing more granular than a camp name and a resource type ever
 * shown (no victim/family data exists anywhere in this shape; see FundsController/CampImpact).
 * There is nothing else for this role to do here, matching FamilyPanel's one-view-per-role
 * precedent for the field shell.
 */
export function DonorImpactPanel() {
  const donations = useMyDonations()
  const impact = useMyImpact()
  const donateFn = useDonate()
  const [submitting, setSubmitting] = useState(false)
  const [failed, setFailed] = useState(false)

  async function handleDonate(disasterId: number, amount: number) {
    setSubmitting(true)
    setFailed(false)
    try {
      await donateFn(disasterId, amount)
      await impact.refetch()
    } catch {
      setFailed(true)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mt-8 flex flex-col gap-6">
      <DonationForm onSubmit={handleDonate} submitting={submitting} failed={failed} />
      {donations.data && donations.data.length > 0 && impact.data && impact.data.disasters.length > 0 && (
        <ImpactChain view={impact.data} />
      )}
    </div>
  )
}

function DonationForm({
  onSubmit,
  submitting,
  failed,
}: {
  onSubmit: (disasterId: number, amount: number) => void
  submitting: boolean
  failed: boolean
}) {
  const { t, i18n } = useTranslation()
  const disastersState = useDisasters()
  const [disasterId, setDisasterId] = useState<number | ''>('')
  const [amount, setAmount] = useState('')

  const disasterOptions = disastersState.status === 'ready' ? disastersState.disasters : []
  const parsedAmount = Number(amount)
  const canSubmit = disasterId !== '' && amount.trim() !== '' && Number.isFinite(parsedAmount) && parsedAmount > 0

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!canSubmit) return
    onSubmit(disasterId as number, parsedAmount)
    setAmount('')
  }

  return (
    <form onSubmit={handleSubmit} className="rounded-2xl border border-line bg-surface p-6 sm:p-8">
      <h2 className="text-xl font-semibold">{t('funds.donate.title')}</h2>
      <p className="mt-2 text-ink-muted">{t('funds.donate.subtitle')}</p>

      <label className="mt-6 block text-sm font-medium text-ink-muted">
        {t('funds.donate.disaster')}
        <select
          value={disasterId}
          onChange={(e) => setDisasterId(e.target.value ? Number(e.target.value) : '')}
          className="mt-1.5 h-12 w-full rounded-xl border border-line bg-bg px-4 text-base text-ink outline-none focus-visible:border-signal focus-visible:ring-2 focus-visible:ring-signal/40"
        >
          <option value="">{t('funds.donate.disasterPlaceholder')}</option>
          {disasterOptions.map((d) => (
            <option key={d.id} value={d.id}>
              {i18n.language === 'bn' ? d.nameBn : d.nameEn}
            </option>
          ))}
        </select>
      </label>

      <label className="mt-4 block text-sm font-medium text-ink-muted">
        {t('funds.donate.amount')}
        <div className="mt-1.5 flex items-center gap-2 rounded-xl border border-line bg-bg px-4 focus-within:border-signal focus-within:ring-2 focus-within:ring-signal/40">
          <span className="text-base text-ink-muted">৳</span>
          <input
            type="number"
            min="0"
            step="any"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder={t('funds.donate.amountPlaceholder')}
            className="h-12 w-full bg-transparent text-base text-ink outline-none"
          />
        </div>
      </label>

      {failed && <p className="mt-4 text-sm text-crit">{t('funds.donate.error')}</p>}

      <button
        type="submit"
        disabled={!canSubmit || submitting}
        className="mt-6 h-12 w-full rounded-xl bg-signal text-base font-semibold text-signal-ink transition-opacity hover:opacity-90 disabled:opacity-60 sm:w-auto sm:px-8"
      >
        {submitting ? t('funds.donate.submitting') : t('funds.donate.submit')}
      </button>
    </form>
  )
}

/**
 * The signature element of this view: a literal unbroken chain from what the donor gave, through
 * the disaster's shared fund, to the camps it reached — each link a plain fact (a name, a
 * resource, an amount), so "proof of impact" reads as a traceable pipeline, not a testimonial.
 */
function ImpactChain({ view }: { view: { disasters: DisasterImpact[]; totalDonatedByMe: number } }) {
  const { t, i18n } = useTranslation()

  return (
    <section className="rounded-2xl border border-line bg-surface p-6 sm:p-8">
      <h2 className="text-xl font-semibold">{t('funds.impact.title')}</h2>
      <p className="mt-2 text-ink-muted">{t('funds.impact.subtitle')}</p>

      <div className="mt-6 flex flex-col gap-8">
        {view.disasters.map((disaster) => (
          <DisasterChain key={disaster.disasterId} disaster={disaster} />
        ))}
      </div>
    </section>
  )

  function DisasterChain({ disaster }: { disaster: DisasterImpact }) {
    const disasterName = i18n.language === 'bn' ? disaster.nameBn : disaster.nameEn
    return (
      <div>
        <div className="flex flex-wrap items-center gap-3">
          <ChainNode label={t('funds.impact.you')} value={formatTaka(disaster.donatedByMe, i18n.language)} tone="signal" />
          <ChainLink />
          <ChainNode label={disasterName} value={t('funds.impact.fund')} tone="muted" />
        </div>

        <ul className="mt-4 ml-1 flex flex-col gap-2 border-l-2 border-dashed border-line pl-5">
          {disaster.camps.map((camp) => {
            const campName = i18n.language === 'bn' ? camp.campNameBn : camp.campNameEn
            return (
              <li key={`${camp.campId}-${camp.resourceType}`} className="relative">
                <span className="absolute top-1/2 -left-[27px] h-2.5 w-2.5 -translate-y-1/2 rounded-full bg-signal" />
                <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5 rounded-lg bg-surface-2 px-3.5 py-2.5">
                  <span className="text-sm font-medium text-ink">{campName}</span>
                  <span className="text-xs text-ink-muted">{t(`camp.resource.${camp.resourceType}`)}</span>
                  <span className="ml-auto font-mono text-sm tabular-nums text-ink">
                    {formatTaka(camp.amountProcured, i18n.language)}
                  </span>
                </div>
              </li>
            )
          })}
        </ul>
      </div>
    )
  }
}

function ChainNode({ label, value, tone }: { label: string; value: string; tone: 'signal' | 'muted' }) {
  return (
    <div className="rounded-xl border border-line bg-surface-2 px-4 py-2.5">
      <p className="text-[11px] tracking-wide text-ink-muted uppercase">{label}</p>
      <p className={`font-mono text-sm tabular-nums ${tone === 'signal' ? 'text-signal' : 'text-ink'}`}>{value}</p>
    </div>
  )
}

function ChainLink() {
  return (
    <svg width="28" height="12" viewBox="0 0 28 12" fill="none" aria-hidden className="shrink-0 text-ink-muted">
      <path d="M1 6h22M18 1l5 5-5 5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function formatTaka(amount: number, language: string): string {
  return `৳${amount.toLocaleString(language, { maximumFractionDigits: 2 })}`
}
