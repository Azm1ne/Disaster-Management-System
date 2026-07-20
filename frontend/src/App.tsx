import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'

type HealthState = 'checking' | 'up' | 'down'

function useApiHealth(): HealthState {
  const [state, setState] = useState<HealthState>('checking')

  useEffect(() => {
    let active = true
    fetch('/api/actuator/health')
      .then((res) => (res.ok ? res.json() : Promise.reject(res.status)))
      .then((body: { status?: string }) => {
        if (active) setState(body.status === 'UP' ? 'up' : 'down')
      })
      .catch(() => {
        if (active) setState('down')
      })
    return () => {
      active = false
    }
  }, [])

  return state
}

function App() {
  const { t, i18n } = useTranslation()
  const health = useApiHealth()

  const toggleLanguage = () => {
    void i18n.changeLanguage(i18n.language === 'en' ? 'bn' : 'en')
  }

  const healthLabel =
    health === 'checking'
      ? t('checking')
      : health === 'up'
        ? t('apiHealthy')
        : t('apiUnreachable')

  return (
    <main className="mx-auto flex min-h-svh max-w-2xl flex-col justify-center gap-8 px-6 py-16">
      <div className="flex items-start justify-between gap-4">
        <div className="space-y-2">
          <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">
            {t('appName')}
          </h1>
          <p className="text-[var(--color-muted-foreground)]">{t('tagline')}</p>
        </div>
        <Button variant="outline" size="sm" onClick={toggleLanguage}>
          {t('language')}
        </Button>
      </div>

      <div className="rounded-lg border border-[var(--color-border)] bg-[var(--color-card)] p-5">
        <p className="text-sm text-[var(--color-muted-foreground)]">
          {t('systemStatus')}
        </p>
        <div className="mt-2 flex items-center gap-2">
          <span
            aria-hidden
            className={
              'h-2.5 w-2.5 rounded-full ' +
              (health === 'up'
                ? 'bg-emerald-500'
                : health === 'down'
                  ? 'bg-red-500'
                  : 'bg-amber-500')
            }
          />
          <span className="font-medium">{healthLabel}</span>
        </div>
      </div>
    </main>
  )
}

export default App
