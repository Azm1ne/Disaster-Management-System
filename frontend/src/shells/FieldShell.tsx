import { useTranslation } from 'react-i18next'
import { useAuth } from '@/auth/AuthContext'
import { LanguageToggle } from '@/components/LanguageToggle'
import { FamilyPanel } from '@/family/FamilyPanel'
import type { RoleConfig } from '@/roles'

/**
 * The calm, large-type, mobile-first shell for people in the field and the public (Victim,
 * Volunteer, Donor, NGO). Light theme, generous spacing, a single clear next step — designed
 * to be usable on a phone, under stress. Placeholder content in this slice.
 */
export function FieldShell({ config }: { config: RoleConfig }) {
  const { t, i18n } = useTranslation()
  const { user, logout } = useAuth()
  const personName = (i18n.language === 'bn' ? user?.nameBn : user?.nameEn) ?? ''
  const roleLabel = t(`roles.${config.key}`)

  return (
    <div data-theme="field" className="flex min-h-svh flex-col bg-bg text-ink">
      <header className="flex items-center gap-3 border-b border-line bg-surface px-5 py-4">
        <div className="flex items-center gap-2">
          <RiverMark />
          <span className="font-mono text-xs tracking-[0.16em] text-ink-muted uppercase">
            {t('appNameShort')}
          </span>
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

      <main className="mx-auto w-full max-w-xl flex-1 px-5 py-10 sm:py-14">
        <span className="inline-flex items-center rounded-full bg-surface-2 px-3 py-1 text-sm font-medium text-signal">
          {roleLabel}
        </span>
        <h1 className="mt-5 text-3xl leading-tight font-bold tracking-tight sm:text-4xl">
          {t('shell.greeting', { name: personName })}
        </h1>
        <p className="mt-3 text-lg text-ink-muted">{t(`roleBlurb.${config.key}`)}</p>

        {config.apiRole === 'VICTIM' ? (
          <FamilyPanel />
        ) : (
          <div className="mt-8 rounded-2xl border border-line bg-surface p-6 sm:p-8">
            <h2 className="text-xl font-semibold">{t('shell.placeholderTitle')}</h2>
            <p className="mt-3 text-ink-muted">
              {t('shell.placeholderBody', { role: roleLabel })}
            </p>
            <button
              type="button"
              className="mt-6 h-12 w-full rounded-xl bg-signal text-base font-semibold text-signal-ink transition-opacity hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-signal focus-visible:ring-offset-2 focus-visible:ring-offset-bg sm:w-auto sm:px-8"
            >
              {t('shell.primaryAction')}
            </button>
          </div>
        )}
      </main>
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
