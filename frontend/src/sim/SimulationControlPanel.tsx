import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/auth/AuthContext'
import {
  SPEEDS,
  pauseSimulation,
  resetSimulation,
  resumeSimulation,
  setSimulationSpeed,
  type SimulationClock,
  type SimulationPhase,
} from '@/sim/api'
import { CLOCK_QUERY_KEY, useSimulationClock } from '@/sim/useSimulationClock'

/** The scripted run in order. The engine is authoritative for which one is current. */
const PHASES: SimulationPhase[] = ['SURGE', 'NEW_CAMP', 'RELIEF_CONVOY', 'RECOVERY']

/** Only these roles may drive the simulation; the server enforces it regardless of the UI. */
const CAN_DRIVE = ['COORDINATOR', 'ADMIN']

function formatSimTime(iso: string, language: string): string {
  return new Intl.DateTimeFormat(language === 'bn' ? 'bn-BD' : 'en-GB', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
    timeZone: 'UTC',
  }).format(new Date(iso))
}

/**
 * The DEMO-badged control for the scripted simulation. Its centre is the storyline: where the
 * run currently sits in the Jamuna scenario, because the point of the demo is that the world is
 * moving through a story rather than drifting randomly. Transport controls sit under it, and
 * Reset is set apart so it is not hit by accident mid-demo.
 */
export function SimulationControlPanel({ onClose }: { onClose: () => void }) {
  const { t, i18n } = useTranslation()
  const { authFetch, user } = useAuth()
  const queryClient = useQueryClient()
  const clock = useSimulationClock()
  const [busy, setBusy] = useState(false)
  const [failed, setFailed] = useState(false)

  const mayDrive = CAN_DRIVE.includes(user?.role ?? '')
  const disabled = !mayDrive || busy || !clock

  const run = async (action: () => Promise<SimulationClock>) => {
    setBusy(true)
    setFailed(false)
    try {
      queryClient.setQueryData(CLOCK_QUERY_KEY, await action())
    } catch {
      setFailed(true)
    } finally {
      setBusy(false)
    }
  }

  const progress = clock ? Math.min(1, clock.tick / clock.scenarioLength) : 0
  const ended = clock ? clock.tick >= clock.scenarioLength : false

  return (
    <aside
      aria-label={t('sim.title')}
      className="flex w-full shrink-0 flex-col gap-5 border-l border-line bg-surface px-5 py-4 md:w-80"
    >
      <header className="flex items-start gap-2">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <h2 className="text-sm font-semibold text-ink">{t('sim.title')}</h2>
            <DemoBadge />
          </div>
          <p className="mt-1 text-xs leading-snug text-ink-muted">{t('sim.subtitle')}</p>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="ml-auto rounded-full border border-line px-2.5 py-1 font-mono text-[10px] tracking-wide text-ink-muted uppercase transition-colors hover:border-line-strong hover:text-ink"
        >
          {t('sim.close')}
        </button>
      </header>

      {/* The simulated moment, in the mono voice the situation room uses for data. */}
      <div>
        <p className="font-mono text-[10px] tracking-[0.16em] text-ink-muted uppercase">
          {t('sim.simTime')}
        </p>
        <p className="mt-1 font-mono text-2xl tabular-nums text-ink">
          {clock ? formatSimTime(clock.simTime, i18n.language) : '—'}
        </p>
        <p className="mt-1 flex items-center gap-2 font-mono text-[11px] text-ink-muted">
          <span
            aria-hidden
            className={`h-1.5 w-1.5 rounded-full ${clock?.running ? 'bg-ok' : 'bg-ink-muted'}`}
          />
          {ended ? t('sim.ended') : clock?.running ? t('sim.running') : t('sim.paused')}
          {clock && (
            <span className="ml-auto tabular-nums">
              {t('sim.tickOf', { tick: clock.tick, total: clock.scenarioLength })}
            </span>
          )}
        </p>
      </div>

      {/* Signature: the storyline. The scenario is a scripted sequence of named phases, so the
          panel shows which one the world is living through, not just an abstract percentage. */}
      <div>
        <p className="font-mono text-[10px] tracking-[0.16em] text-ink-muted uppercase">
          {t('sim.storyline')}
        </p>
        <div className="mt-2 h-1 w-full overflow-hidden rounded-full bg-surface-2">
          <div
            className="h-full rounded-full bg-signal transition-[width] duration-500 ease-out"
            style={{ width: `${progress * 100}%` }}
          />
        </div>
        <ol className="mt-3 flex flex-col gap-1">
          {PHASES.map((phase) => {
            const active = clock?.phase === phase
            return (
              <li
                key={phase}
                aria-current={active ? 'step' : undefined}
                className={`flex items-center gap-2 rounded-md border px-2.5 py-1.5 text-xs transition-colors ${
                  active
                    ? 'border-signal/40 bg-signal/15 font-medium text-ink'
                    : 'border-transparent text-ink-muted'
                }`}
              >
                <span
                  aria-hidden
                  className={`h-1.5 w-1.5 shrink-0 rounded-full ${active ? 'bg-signal' : 'bg-line-strong'}`}
                />
                {t(`sim.phase.${phase}`)}
              </li>
            )
          })}
        </ol>
      </div>

      <div className="flex flex-col gap-3">
        <button
          type="button"
          disabled={disabled}
          onClick={() => run(() => (clock?.running ? pauseSimulation : resumeSimulation)(authFetch))}
          className="h-10 rounded-lg bg-signal text-sm font-semibold text-signal-ink transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {clock?.running ? t('sim.pause') : t('sim.resume')}
        </button>

        <div>
          <p className="font-mono text-[10px] tracking-[0.16em] text-ink-muted uppercase">
            {t('sim.speed')}
          </p>
          <div role="group" aria-label={t('sim.speed')} className="mt-2 flex gap-1">
            {SPEEDS.map((speed) => {
              const current = clock?.speed === speed
              return (
                <button
                  key={speed}
                  type="button"
                  disabled={disabled}
                  aria-pressed={current}
                  onClick={() => run(() => setSimulationSpeed(authFetch, speed))}
                  className={`h-8 flex-1 rounded-md border font-mono text-[11px] tabular-nums transition-colors disabled:cursor-not-allowed disabled:opacity-40 ${
                    current
                      ? 'border-signal bg-signal/15 text-ink'
                      : 'border-line text-ink-muted hover:border-line-strong hover:text-ink'
                  }`}
                >
                  {speed}×
                </button>
              )
            })}
          </div>
        </div>
      </div>

      <div className="mt-auto border-t border-line pt-4">
        <button
          type="button"
          disabled={disabled}
          onClick={() => run(() => resetSimulation(authFetch))}
          className="h-9 w-full rounded-lg border border-line text-xs text-ink-muted transition-colors hover:border-crit hover:text-ink disabled:cursor-not-allowed disabled:opacity-40"
        >
          {t('sim.reset')}
        </button>
        {!mayDrive && <p className="mt-2 text-xs leading-snug text-ink-muted">{t('sim.restricted')}</p>}
        {failed && <p className="mt-2 text-xs leading-snug text-crit">{t('sim.failed')}</p>}
      </div>
    </aside>
  )
}

export function DemoBadge() {
  const { t } = useTranslation()
  return (
    <span className="rounded-sm bg-signal px-1.5 py-0.5 font-mono text-[10px] font-semibold tracking-[0.15em] text-signal-ink">
      {t('ribbon.demo')}
    </span>
  )
}
