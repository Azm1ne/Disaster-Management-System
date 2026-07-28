import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/auth/AuthContext'
import { LanguageToggle } from '@/components/LanguageToggle'
import { useApproveProposal, usePendingProposals, useRejectProposal } from '@/proposals/useProposals'
import type { Proposal } from '@/proposals/api'

/**
 * The Central Authority's entire world: one page, one job — clear or reject whatever a
 * coordinator has proposed. Deliberately not an `OperatorShell` tab: this role never reads the
 * live map, never touches allocations or comms, so a sidebar built for those tools would just be
 * dead chrome around a single list. It borrows the operator theme's dark, high-signal palette
 * (this is still a command decision, not a field task) but strips the shell down to a header and
 * a queue — closer to a dispatch ledger than a dashboard. No filtering, sorting, or history: the
 * queue is short by design, and once a proposal is decided it leaves the list for good.
 */
export function ProposalInbox() {
  const { t, i18n } = useTranslation()
  const { user, logout } = useAuth()
  const { data: proposals, isPending, isError } = usePendingProposals()
  const personName = (i18n.language === 'bn' ? user?.nameBn : user?.nameEn) ?? ''

  return (
    <div data-theme="operator" className="min-h-svh bg-bg text-ink">
      <header className="flex items-center gap-3 border-b border-line px-5 py-4 sm:px-8">
        <div className="min-w-0">
          <p className="font-mono text-[11px] tracking-wide text-ink-muted uppercase">
            {t('roles.central_authority')}
          </p>
          <p className="truncate text-sm font-medium">{personName}</p>
        </div>
        <div className="ml-auto flex items-center gap-2">
          <LanguageToggle />
          <button
            type="button"
            onClick={logout}
            className="inline-flex h-9 items-center rounded-full border border-line px-3.5 text-xs text-ink-muted transition-colors hover:border-crit hover:text-ink"
          >
            {t('shell.logout')}
          </button>
        </div>
      </header>

      <main className="mx-auto max-w-2xl px-5 py-8 sm:px-8">
        <div className="mb-6">
          <h1 className="text-lg font-semibold text-ink">{t('centralAuthority.title')}</h1>
          <p className="mt-1 text-sm text-ink-muted">{t('centralAuthority.subtitle')}</p>
        </div>

        {isPending && <p className="text-sm text-ink-muted">{t('centralAuthority.loading')}</p>}
        {isError && <p className="text-sm text-crit">{t('centralAuthority.error')}</p>}
        {proposals && proposals.length === 0 && (
          <p className="text-sm text-ink-muted">{t('centralAuthority.empty')}</p>
        )}
        {proposals && proposals.length > 0 && (
          <ul className="flex flex-col gap-3">
            {proposals.map((proposal) => (
              <ProposalCard key={proposal.id} proposal={proposal} />
            ))}
          </ul>
        )}
      </main>
    </div>
  )
}

function summarize(t: (key: string, options?: Record<string, unknown>) => string, proposal: Proposal): string {
  const p = proposal.payload
  switch (proposal.proposalType) {
    case 'DISASTER_CREATE':
      return t('proposals.summary.disasterCreate', { code: p.code })
    case 'DISASTER_UPDATE':
      return t('proposals.summary.disasterUpdate', { id: proposal.targetDisasterId })
    case 'DISASTER_CLOSE':
      return t('proposals.summary.disasterClose', { id: proposal.targetDisasterId })
    case 'AFFECTED_AREA_CREATE':
      return t('proposals.summary.affectedAreaCreate', { name: p.nameEn })
    case 'CAMP_CREATE':
      return t('proposals.summary.campCreate', { code: p.code, population: p.initialPopulation })
  }
}

function ProposalCard({ proposal }: { proposal: Proposal }) {
  const { t } = useTranslation()
  const approve = useApproveProposal()
  const reject = useRejectProposal()
  const [confirmingReject, setConfirmingReject] = useState(false)
  const [busy, setBusy] = useState(false)

  async function handleApprove() {
    setBusy(true)
    await approve(proposal.id)
  }

  async function handleReject() {
    if (!confirmingReject) {
      setConfirmingReject(true)
      return
    }
    setBusy(true)
    await reject(proposal.id)
  }

  return (
    <li className="rounded-xl border border-line bg-surface p-4 sm:p-5">
      <p className="font-mono text-[10px] tracking-[0.14em] text-signal uppercase">
        {t(`proposals.type.${proposal.proposalType}`)}
      </p>
      <p className="mt-1.5 text-sm text-ink">{summarize(t, proposal)}</p>
      <p className="mt-1 text-xs text-ink-muted">
        {t('proposals.proposedBy', { id: proposal.proposedByUserId })}
      </p>

      <div className="mt-3 flex flex-wrap items-center gap-2 border-t border-line pt-3">
        <button
          type="button"
          disabled={busy}
          onClick={handleApprove}
          className="inline-flex h-8 items-center rounded-full bg-signal px-4 text-xs font-semibold text-signal-ink transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {t('proposals.approve')}
        </button>
        <button
          type="button"
          disabled={busy}
          onClick={handleReject}
          className={
            confirmingReject
              ? 'inline-flex h-8 items-center rounded-full bg-crit px-4 text-xs font-semibold text-signal-ink transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-40'
              : 'inline-flex h-8 items-center rounded-full border border-line px-4 text-xs text-ink-muted transition-colors hover:border-crit hover:text-ink disabled:cursor-not-allowed disabled:opacity-40'
          }
        >
          {t(confirmingReject ? 'proposals.rejectConfirm' : 'proposals.reject')}
        </button>
      </div>
    </li>
  )
}
