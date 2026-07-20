import { useTranslation } from 'react-i18next'

/**
 * The EN↔বাংলা switch. Present on every screen (login and both shells); the label always
 * shows the language you would switch to. The choice is persisted in i18n.ts.
 */
export function LanguageToggle({ className = '' }: { className?: string }) {
  const { t, i18n } = useTranslation()
  const next = i18n.language === 'en' ? 'bn' : 'en'

  return (
    <button
      type="button"
      onClick={() => void i18n.changeLanguage(next)}
      aria-label={`Switch language to ${next === 'bn' ? 'বাংলা' : 'English'}`}
      className={
        'inline-flex h-9 items-center rounded-full border border-line px-3.5 font-mono text-xs ' +
        'tracking-wide text-ink-muted transition-colors hover:border-line-strong hover:text-ink ' +
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-signal ' +
        className
      }
    >
      {t('switchLanguage')}
    </button>
  )
}
