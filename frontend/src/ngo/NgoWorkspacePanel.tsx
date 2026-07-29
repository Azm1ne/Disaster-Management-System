import { useTranslation } from 'react-i18next'
import { useDisasters } from '@/world/useDisasters'
import { useFundsReport } from '@/funds/useFunds'
import { FundsReportTable } from '@/funds/FundsReportTable'
import type { Disaster } from '@/world/api'

/**
 * The NGO partner's read-only workspace (ticket 15). Two sections, deliberately no write
 * affordances:
 *
 *   1. *Your disaster engagement* — a one-line summary per disaster (name + camp count) so the
 *      NGO partner can see the operations they're contributing into.
 *   2. *Unaccounted funds* — the same Donated/Procured/Unaccounted table the Coordinator/Admin
 *      workspace uses, sourced from the existing `/funds/report` endpoint. The NGO role has
 *      read access to that report by design (see `FundLedgerService.report`); the write-side
 *      procurement tool stays Coordinator/Admin only.
 *
 * Per docs/responsible-design-note.md and docs/comms-scope.md, NGO visibility is read-only
 * transparency — no chat surface, no procurement, no proposal-write. This panel is exactly
 * that: a window, not a control.
 */
export function NgoWorkspacePanel() {
  const { t } = useTranslation()
  const disastersState = useDisasters()
  const report = useFundsReport()

  const disasters = disastersState.status === 'ready' ? disastersState.disasters : []

  return (
    <div className="mt-8 flex flex-col gap-8">
      <section>
        <h2 className="text-xl font-semibold text-ink">{t('ngo.engagement.title')}</h2>
        <p className="mt-1 text-sm text-ink-muted">{t('ngo.engagement.subtitle')}</p>

        {disastersState.status === 'loading' ? (
          <p className="mt-4 text-sm text-ink-muted">{t('ngo.engagement.loading')}</p>
        ) : disastersState.status === 'error' ? (
          <p className="mt-4 text-sm text-ink-muted">{t('ngo.engagement.error')}</p>
        ) : disasters.length === 0 ? (
          <p className="mt-4 text-sm text-ink-muted">{t('ngo.engagement.empty')}</p>
        ) : (
          <ul className="mt-4 flex flex-col gap-3">
            {disasters.map((disaster) => (
              <DisasterEngagementCard key={disaster.id} disaster={disaster} />
            ))}
          </ul>
        )}
      </section>

      <section>
        <h2 className="text-xl font-semibold text-ink">{t('ngo.report.title')}</h2>
        <p className="mt-1 text-sm text-ink-muted">{t('ngo.report.subtitle')}</p>

        {report.data ? (
          <div className="mt-4">
            <FundsReportTable report={report.data} />
          </div>
        ) : (
          <p className="mt-4 text-sm text-ink-muted">{t('ngo.report.loading')}</p>
        )}
      </section>
    </div>
  )
}

/** A single disaster's engagement summary —— the disaster name (bilingual) plus a neutral one-line
 * "n camps" summary, nothing about family/victim data, nothing operational the NGO can't already see.
 */
function DisasterEngagementCard({ disaster }: { disaster: Disaster }) {
  const { t, i18n } = useTranslation()
  const name = i18n.language === 'bn' ? disaster.nameBn : disaster.nameEn
  const campCount = disaster.camps.length
  return (
    <li className="rounded-2xl border border-line bg-surface p-5">
      <div className="flex items-center justify-between gap-3">
        <span className="text-base font-semibold text-ink">{name}</span>
        <span className="inline-flex items-center rounded-full bg-surface-2 px-3 py-1 text-sm font-medium text-signal">
          {t('ngo.engagement.campsCount', { count: campCount })}
        </span>
      </div>
      <p className="mt-1 text-sm text-ink-muted">
        {t(`map.disasterStatus.${disaster.status === 'ACTIVE' ? 'active' : 'stable'}`)}
      </p>
    </li>
  )
}
