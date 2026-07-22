import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { AlertWorkspace } from '@/alerts/AlertWorkspace'
import { useAuth } from '@/auth/AuthContext'
import { LanguageToggle } from '@/components/LanguageToggle'
import { StatusRibbon } from '@/shells/StatusRibbon'
import { DemoBadge, SimulationControlPanel } from '@/sim/SimulationControlPanel'
import { MyCampPanel } from '@/world/MyCampPanel'
import { WorldWorkspace } from '@/world/WorldWorkspace'
import type { RoleConfig } from '@/roles'

const NAV_PLACEHOLDER = ['camps', 'resources', 'people'] as const

/**
 * The dense situation-room shell for the people running the operation (Coordinator, Admin,
 * Camp Manager). Chrome only in this slice: the status ribbon, an operations sidebar, and a
 * role-labelled landing. The live map and tools plug into the main region in the next slice.
 */
export function OperatorShell({ config }: { config: RoleConfig }) {
  const { t, i18n } = useTranslation()
  const { user, logout } = useAuth()
  const [simOpen, setSimOpen] = useState(false)
  const personName = (i18n.language === 'bn' ? user?.nameBn : user?.nameEn) ?? ''
  const roleLabel = t(`roles.${config.key}`)

  return (
    <div data-theme="operator" className="flex min-h-svh flex-col bg-bg text-ink">
      <StatusRibbon />

      <div className="flex flex-1">
        <aside className="hidden w-60 shrink-0 flex-col border-r border-line bg-surface md:flex">
          <div className="flex items-center gap-2 px-5 py-4">
            <RiverMark />
            <span className="font-mono text-xs tracking-[0.16em] text-ink-muted uppercase">
              {t('appNameShort')}
            </span>
          </div>
          <nav className="flex flex-col gap-0.5 px-3 py-2">
            <span className="rounded-md bg-surface-2 px-3 py-2 text-sm font-medium text-ink">
              {t('nav.overview')}
            </span>
            <span className="rounded-md px-3 py-2 text-sm text-ink">{t('nav.alerts')}</span>
            {NAV_PLACEHOLDER.map((item) => (
              <span
                key={item}
                className="flex items-center justify-between rounded-md px-3 py-2 text-sm text-ink-muted"
              >
                {t(`nav.${item}`)}
                <span className="font-mono text-[10px] tracking-wide text-ink-muted/70 uppercase">
                  {t('nav.soon')}
                </span>
              </span>
            ))}
          </nav>
        </aside>

        <div className="flex min-w-0 flex-1 flex-col">
          <header className="flex items-center gap-3 border-b border-line px-4 py-3 sm:px-6">
            <div className="min-w-0">
              <p className="font-mono text-[11px] tracking-wide text-ink-muted uppercase">
                {roleLabel}
              </p>
              <p className="truncate text-sm font-medium">{personName}</p>
            </div>
            <div className="ml-auto flex items-center gap-2">
              {!simOpen && (
                <button
                  type="button"
                  onClick={() => setSimOpen(true)}
                  className="inline-flex h-9 items-center gap-2 rounded-full border border-line px-3.5 text-xs text-ink-muted transition-colors hover:border-line-strong hover:text-ink"
                >
                  {t('sim.title')}
                  <DemoBadge />
                </button>
              )}
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

          <div className="flex min-h-0 flex-1 flex-col md:flex-row">
            <main className="flex min-h-0 flex-1 flex-col">
              {config.apiRole === 'CAMP_MANAGER' && <MyCampPanel />}
              <div className="min-h-0 flex-1 overflow-y-auto">
                <WorldWorkspace />
                <AlertWorkspace />
              </div>
            </main>
            {simOpen && <SimulationControlPanel onClose={() => setSimOpen(false)} />}
          </div>
        </div>
      </div>
    </div>
  )
}

function RiverMark() {
  return (
    <svg width="20" height="20" viewBox="0 0 22 22" fill="none" aria-hidden>
      <path
        d="M2 6c5 0 5 5 9 5s4-5 9-5M2 16c5 0 5-5 9-5"
        stroke="var(--signal)"
        strokeWidth="1.75"
        strokeLinecap="round"
      />
    </svg>
  )
}
