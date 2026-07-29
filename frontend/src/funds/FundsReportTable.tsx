import { useTranslation } from 'react-i18next'
import type { FundsReport } from '@/funds/api'

/**
 * The unaccounted-funds table — one row per disaster (Donated, Procured, Unaccounted) plus a
 * totals footer. Shared between the Coordinator/Admin funds workspace (the write-side auditing
 * tool that pairs it with the procurement form) and the NGO read-only workspace (which gets just
 * the table, by design — see docs/responsible-design-note.md). Keeping it as its own component
 * guarantees the read-only viewer and the write-side viewer stay visually identical.
 */
export function FundsReportTable({ report }: { report: FundsReport }) {
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

export function formatTaka(amount: number, language: string): string {
  return `৳${amount.toLocaleString(language, { maximumFractionDigits: 2 })}`
}
