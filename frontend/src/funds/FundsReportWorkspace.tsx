import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useDisasters } from '@/world/useDisasters'
import { useFundsReport, useProcure } from '@/funds/useFunds'
import type { ResourceType } from '@/funds/api'

const RESOURCE_TYPES: ResourceType[] = ['WATER', 'FOOD', 'MEDICAL']

/** Coordinator/Admin's view of the money pipeline: procure against a disaster's fund balance,
 * and read back the unaccounted-funds report — every disaster's money in, aid out (via
 * procurement), and the gap between them, which is the audit tool this ticket exists for. */
export function FundsReportWorkspace() {
  const { t } = useTranslation()
  const report = useFundsReport()

  return (
    <section className="flex flex-col gap-4 p-4 sm:p-6">
      <div>
        <h2 className="text-lg font-semibold text-ink">{t('funds.report.title')}</h2>
        <p className="text-sm text-ink-muted">{t('funds.report.subtitle')}</p>
      </div>

      <ProcurementForm />

      {report.data && <ReportTable report={report.data} />}
    </section>
  )
}

function ReportTable({
  report,
}: {
  report: NonNullable<ReturnType<typeof useFundsReport>['data']>
}) {
  const { t, i18n } = useTranslation()

  return (
    <div className="overflow-x-auto rounded-lg border border-line">
      <table className="w-full min-w-[520px] border-collapse text-sm">
        <thead>
          <tr className="border-b border-line bg-surface-2 text-left text-xs text-ink-muted uppercase">
            <th className="px-4 py-2.5 font-medium">{t('funds.report.disaster')}</th>
            <th className="px-4 py-2.5 text-right font-medium">{t('funds.report.donated')}</th>
            <th className="px-4 py-2.5 text-right font-medium">{t('funds.report.procured')}</th>
            <th className="px-4 py-2.5 text-right font-medium">{t('funds.report.unaccounted')}</th>
          </tr>
        </thead>
        <tbody>
          {report.disasters.map((d) => (
            <tr key={d.disasterId} className="border-b border-line last:border-0">
              <td className="px-4 py-2.5 text-ink">{i18n.language === 'bn' ? d.nameBn : d.nameEn}</td>
              <td className="px-4 py-2.5 text-right font-mono tabular-nums text-ink">{formatTaka(d.donated, i18n.language)}</td>
              <td className="px-4 py-2.5 text-right font-mono tabular-nums text-ink">{formatTaka(d.procured, i18n.language)}</td>
              <td className="px-4 py-2.5 text-right font-mono tabular-nums text-signal">
                {formatTaka(d.unaccounted, i18n.language)}
              </td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr className="bg-surface-2 font-medium text-ink">
            <td className="px-4 py-2.5">{t('funds.report.total')}</td>
            <td className="px-4 py-2.5 text-right font-mono tabular-nums">{formatTaka(report.totalDonated, i18n.language)}</td>
            <td className="px-4 py-2.5 text-right font-mono tabular-nums">{formatTaka(report.totalProcured, i18n.language)}</td>
            <td className="px-4 py-2.5 text-right font-mono tabular-nums text-signal">
              {formatTaka(report.totalUnaccounted, i18n.language)}
            </td>
          </tr>
        </tfoot>
      </table>
    </div>
  )
}

function ProcurementForm() {
  const { t, i18n } = useTranslation()
  const disastersState = useDisasters()
  const procureFn = useProcure()
  const [disasterId, setDisasterId] = useState<number | ''>('')
  const [campId, setCampId] = useState<number | ''>('')
  const [resourceType, setResourceType] = useState<ResourceType>('WATER')
  const [amount, setAmount] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [failed, setFailed] = useState(false)

  const disasters = disastersState.status === 'ready' ? disastersState.disasters : []
  const camps = disasters.find((d) => d.id === disasterId)?.camps ?? []
  const parsedAmount = Number(amount)
  const canSubmit = disasterId !== '' && campId !== '' && amount.trim() !== '' && Number.isFinite(parsedAmount) && parsedAmount > 0

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!canSubmit) return
    setSubmitting(true)
    setFailed(false)
    try {
      await procureFn(disasterId as number, campId as number, resourceType, parsedAmount)
      setAmount('')
    } catch {
      setFailed(true)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="rounded-lg border border-line bg-surface p-4">
      <h3 className="text-sm font-semibold text-ink">{t('funds.procure.title')}</h3>
      <p className="mt-1 text-xs text-ink-muted">{t('funds.procure.subtitle')}</p>

      <div className="mt-3 flex flex-wrap items-end gap-2">
        <label className="flex flex-col text-xs text-ink-muted">
          {t('funds.procure.disaster')}
          <select
            value={disasterId}
            onChange={(e) => {
              setDisasterId(e.target.value ? Number(e.target.value) : '')
              setCampId('')
            }}
            className="mt-1 h-9 rounded-md border border-line bg-bg px-2 text-sm text-ink outline-none focus-visible:border-signal"
          >
            <option value="">{t('funds.procure.disasterPlaceholder')}</option>
            {disasters.map((d) => (
              <option key={d.id} value={d.id}>
                {i18n.language === 'bn' ? d.nameBn : d.nameEn}
              </option>
            ))}
          </select>
        </label>

        <label className="flex flex-col text-xs text-ink-muted">
          {t('funds.procure.camp')}
          <select
            value={campId}
            onChange={(e) => setCampId(e.target.value ? Number(e.target.value) : '')}
            disabled={camps.length === 0}
            className="mt-1 h-9 rounded-md border border-line bg-bg px-2 text-sm text-ink outline-none focus-visible:border-signal disabled:opacity-50"
          >
            <option value="">{t('funds.procure.campPlaceholder')}</option>
            {camps.map((c) => (
              <option key={c.id} value={c.id}>
                {i18n.language === 'bn' ? c.nameBn : c.nameEn}
              </option>
            ))}
          </select>
        </label>

        <label className="flex flex-col text-xs text-ink-muted">
          {t('funds.procure.resource')}
          <select
            value={resourceType}
            onChange={(e) => setResourceType(e.target.value as ResourceType)}
            className="mt-1 h-9 rounded-md border border-line bg-bg px-2 text-sm text-ink outline-none focus-visible:border-signal"
          >
            {RESOURCE_TYPES.map((r) => (
              <option key={r} value={r}>
                {t(`camp.resource.${r}`)}
              </option>
            ))}
          </select>
        </label>

        <label className="flex flex-col text-xs text-ink-muted">
          {t('funds.procure.amount')}
          <input
            type="number"
            min="0"
            step="any"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="৳"
            className="mt-1 h-9 w-28 rounded-md border border-line bg-bg px-2 text-sm text-ink outline-none focus-visible:border-signal"
          />
        </label>

        <button
          type="submit"
          disabled={!canSubmit || submitting}
          className="h-9 rounded-md bg-signal px-4 text-sm font-semibold text-signal-ink transition-opacity hover:opacity-90 disabled:opacity-50"
        >
          {submitting ? t('funds.procure.submitting') : t('funds.procure.submit')}
        </button>
      </div>

      {failed && <p className="mt-2 text-xs text-crit">{t('funds.procure.error')}</p>}
    </form>
  )
}

function formatTaka(amount: number, language: string): string {
  return `৳${amount.toLocaleString(language, { maximumFractionDigits: 2 })}`
}
