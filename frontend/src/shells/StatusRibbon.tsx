import { useTranslation } from 'react-i18next'
import { useRealtime } from '@/realtime/RealtimeProvider'
import { useSimulationClock } from '@/sim/useSimulationClock'

/**
 * The situation-room heartbeat: a thin monospace strip pinned to the top of every operator
 * screen, naming the disaster being run and carrying a DEMO badge that must never be mistaken
 * for a production control. The clock is the simulation's own clock, so the strip reads the
 * simulated moment and the phase of the story, and shows whether the run is playing or held.
 */
export function StatusRibbon() {
  const { t, i18n } = useTranslation()
  const { connected } = useRealtime()
  const clock = useSimulationClock()

  const simTime = clock
    ? new Intl.DateTimeFormat(i18n.language === 'bn' ? 'bn-BD' : 'en-GB', {
        day: 'numeric',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit',
        timeZone: 'UTC',
      }).format(new Date(clock.simTime))
    : '—'

  const playing = clock?.running ?? false

  return (
    <div className="flex items-center gap-x-4 gap-y-1 border-b border-line bg-surface px-4 py-2 font-mono text-[11px] tracking-wide sm:px-6">
      <span className="flex items-center gap-2 text-ink">
        <span className="h-1.5 w-1.5 rounded-full bg-ok" aria-hidden />
        <span className="font-semibold">{t('ribbon.disaster')}</span>
        <span className="hidden text-ink-muted sm:inline">· {t('ribbon.region')}</span>
      </span>

      <span className="ml-auto flex items-center gap-3 text-ink-muted">
        {clock && (
          <span className="hidden text-ink-muted sm:inline">{t(`sim.phase.${clock.phase}`)}</span>
        )}
        <span className="hidden items-center gap-1.5 sm:flex">
          <span
            aria-hidden
            className={`h-1.5 w-1.5 rounded-full ${playing && connected ? 'bg-ok' : 'bg-ink-muted'}`}
          />
          {playing ? t('ribbon.live') : t('sim.paused')}
        </span>
        <span aria-label={t('ribbon.clockLabel')} className="tabular-nums text-ink">
          {simTime}
        </span>
        <span className="rounded-sm bg-signal px-1.5 py-0.5 font-semibold tracking-[0.15em] text-signal-ink">
          {t('ribbon.demo')}
        </span>
      </span>
    </div>
  )
}
