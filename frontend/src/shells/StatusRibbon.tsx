import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

/**
 * The situation-room heartbeat: a thin monospace strip pinned to the top of every operator
 * screen, naming the disaster being run and carrying a DEMO badge that must never be mistaken
 * for a production control. The clock is wall-clock for now; it binds to the simulation clock
 * when the engine lands in the next slice.
 */
export function StatusRibbon() {
  const { t } = useTranslation()
  const clock = useClock()

  return (
    <div className="flex items-center gap-x-4 gap-y-1 border-b border-line bg-surface px-4 py-2 font-mono text-[11px] tracking-wide sm:px-6">
      <span className="flex items-center gap-2 text-ink">
        <span className="h-1.5 w-1.5 rounded-full bg-ok" aria-hidden />
        <span className="font-semibold">{t('ribbon.disaster')}</span>
        <span className="hidden text-ink-muted sm:inline">· {t('ribbon.region')}</span>
      </span>

      <span className="ml-auto flex items-center gap-3 text-ink-muted">
        <span className="hidden items-center gap-1.5 sm:flex">
          <span className="h-1.5 w-1.5 rounded-full bg-ok" aria-hidden />
          {t('ribbon.live')}
        </span>
        <span aria-label={t('ribbon.clockLabel')} className="tabular-nums text-ink">
          {clock}
        </span>
        <span className="rounded-sm bg-signal px-1.5 py-0.5 font-semibold tracking-[0.15em] text-signal-ink">
          {t('ribbon.demo')}
        </span>
      </span>
    </div>
  )
}

function useClock(): string {
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000)
    return () => clearInterval(id)
  }, [])
  return now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}
