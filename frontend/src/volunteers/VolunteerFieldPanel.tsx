import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAcceptShift, useMyAssignments, useRouteFetcher, useVolunteerTasks } from '@/volunteers/useVolunteers'
import type { RouteView, VolunteerTaskSummary } from '@/volunteers/api'

/** The volunteer's whole world in one screen: shifts still open for the taking, then everything
 * already theirs (push-assigned or self-accepted) with a route to get there. Light, large-type,
 * mobile-first — the FieldShell convention — since this is read on a phone, in the field. */
export function VolunteerFieldPanel() {
  const { t } = useTranslation()

  return (
    <div className="mt-8 flex flex-col gap-8">
      <section>
        <h2 className="text-xl font-semibold text-ink">{t('volunteers.myShifts')}</h2>
        <p className="mt-1 text-sm text-ink-muted">{t('volunteers.myShiftsBlurb')}</p>
        <MyShifts />
      </section>

      <section>
        <h2 className="text-xl font-semibold text-ink">{t('volunteers.openShifts')}</h2>
        <p className="mt-1 text-sm text-ink-muted">{t('volunteers.openShiftsBlurb')}</p>
        <OpenShifts />
      </section>
    </div>
  )
}

function MyShifts() {
  const { t } = useTranslation()
  const tasks = useMyAssignments()

  if (!tasks) {
    return null
  }
  if (tasks.length === 0) {
    return <p className="mt-4 text-sm text-ink-muted">{t('volunteers.noMyShifts')}</p>
  }

  return (
    <ul className="mt-4 flex flex-col gap-3">
      {tasks.map((task) => (
        <MyShiftCard key={task.id} task={task} />
      ))}
    </ul>
  )
}

function MyShiftCard({ task }: { task: VolunteerTaskSummary }) {
  const { t } = useTranslation()
  const fetchRoute = useRouteFetcher()
  const [route, setRoute] = useState<RouteView | null>(null)
  const [loading, setLoading] = useState(false)

  return (
    <li data-task-id={task.id} className="rounded-2xl border border-line bg-surface p-5">
      <div className="flex items-center justify-between">
        <span className="inline-flex items-center rounded-full bg-surface-2 px-3 py-1 text-sm font-medium text-signal">
          {t(`volunteers.skill.${task.requiredSkill}`)}
        </span>
        <span className="text-xs font-medium text-ink-muted">{t(`volunteers.method.${task.assignmentMethod}`)}</span>
      </div>
      <p className="mt-2 text-base text-ink">{task.description}</p>

      {route ? (
        <RouteSketch route={route} />
      ) : (
        <button
          type="button"
          disabled={loading}
          onClick={async () => {
            setLoading(true)
            try {
              setRoute(await fetchRoute(task.id))
            } finally {
              setLoading(false)
            }
          }}
          className="mt-4 inline-flex h-11 w-full items-center justify-center rounded-xl border border-line text-sm font-medium text-ink transition-colors hover:border-line-strong disabled:opacity-50 sm:w-auto sm:px-6"
        >
          {loading ? t('volunteers.loadingRoute') : t('volunteers.viewRoute')}
        </button>
      )}
    </li>
  )
}

/** A minimal route sketch: the polyline traced to fit a small box, plus the numbers that matter
 * (distance, ETA, and whether it's a real road route or the straight-line fallback) — deliberately
 * not a full map (no tile dependency, no API key), but still gives a volunteer a felt sense of the
 * path, not just digits. */
function RouteSketch({ route }: { route: RouteView }) {
  const { t, i18n } = useTranslation()
  const { d, startX, startY, endX, endY } = projectToSvgPath(route.points)
  const km = (route.distanceMeters / 1000).toLocaleString(i18n.language, { maximumFractionDigits: 1 })
  const minutes = Math.round(route.durationSeconds / 60)

  return (
    <div className="mt-4 rounded-xl border border-line bg-surface-2 p-4">
      <svg viewBox="0 0 280 100" className="h-20 w-full" aria-hidden>
        <path d={d} fill="none" stroke="var(--signal)" strokeWidth="2.5" strokeLinecap="round" />
        <circle cx={startX} cy={startY} r="4" fill="var(--signal)" />
        <circle cx={endX} cy={endY} r="4" fill="var(--crit)" />
      </svg>
      <div className="mt-2 flex items-center justify-between text-sm">
        <span className="font-mono tabular-nums text-ink">
          {t('volunteers.routeSummary', { km, minutes })}
        </span>
        <span className="text-xs text-ink-muted">
          {route.source === 'OSRM' ? t('volunteers.routeSourceOsrm') : t('volunteers.routeSourceStraight')}
        </span>
      </div>
    </div>
  )
}

/** Projects lat/lng points into a 280x100 viewBox, padded so the line never touches the edge. */
function projectToSvgPath(points: [number, number][]) {
  const pad = 10
  const width = 280 - pad * 2
  const height = 100 - pad * 2
  const lats = points.map((p) => p[0])
  const lngs = points.map((p) => p[1])
  const minLat = Math.min(...lats)
  const maxLat = Math.max(...lats)
  const minLng = Math.min(...lngs)
  const maxLng = Math.max(...lngs)
  const spanLat = maxLat - minLat || 1
  const spanLng = maxLng - minLng || 1

  const project = ([lat, lng]: [number, number]) => {
    const x = pad + ((lng - minLng) / spanLng) * width
    const y = pad + (1 - (lat - minLat) / spanLat) * height
    return [x, y] as const
  }

  const projected = points.map(project)
  const [startX, startY] = projected[0]
  const [endX, endY] = projected[projected.length - 1]
  const d = projected.map(([x, y], i) => `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`).join(' ')

  return { d, startX, startY, endX, endY }
}

function OpenShifts() {
  const { t } = useTranslation()
  const tasks = useVolunteerTasks()
  const accept = useAcceptShift()
  const [acceptingId, setAcceptingId] = useState<number | null>(null)

  if (!tasks) {
    return null
  }
  if (tasks.length === 0) {
    return <p className="mt-4 text-sm text-ink-muted">{t('volunteers.noOpenShifts')}</p>
  }

  return (
    <ul className="mt-4 flex flex-col gap-3">
      {tasks.map((task) => (
        <li key={task.id} data-task-id={task.id} className="rounded-2xl border border-line bg-surface p-5">
          <span className="inline-flex items-center rounded-full bg-surface-2 px-3 py-1 text-sm font-medium text-signal">
            {t(`volunteers.skill.${task.requiredSkill}`)}
          </span>
          <p className="mt-2 text-base text-ink">{task.description}</p>
          <button
            type="button"
            disabled={acceptingId === task.id}
            onClick={async () => {
              setAcceptingId(task.id)
              try {
                await accept(task.id)
              } finally {
                setAcceptingId(null)
              }
            }}
            className="mt-4 inline-flex h-11 w-full items-center justify-center rounded-xl bg-signal text-sm font-semibold text-signal-ink transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50 sm:w-auto sm:px-6"
          >
            {t('volunteers.acceptShift')}
          </button>
        </li>
      ))}
    </ul>
  )
}
