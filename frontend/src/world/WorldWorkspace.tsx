import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import type { Disaster } from '@/world/api'
import { DISASTER_COLOR } from '@/world/mapTheme'
import { useDisasters } from '@/world/useDisasters'
import { WorldMap } from '@/world/WorldMap'

/**
 * The operator's map-first workspace: a thin summary of every live disaster across the top,
 * the dual-color map filling the rest. This is the whole picture at a glance — the design's
 * situation room, now driven by real seeded data.
 */
export function WorldWorkspace() {
  const { t } = useTranslation()
  const state = useDisasters()

  if (state.status === 'loading') {
    return <Centered>{t('map.loading')}</Centered>
  }
  if (state.status === 'error') {
    return <Centered>{t('map.error')}</Centered>
  }

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex flex-wrap gap-2 border-b border-line px-4 py-3 sm:px-6">
        {state.disasters.map((disaster) => (
          <DisasterChip key={disaster.id} disaster={disaster} />
        ))}
      </div>
      <div className="min-h-0 flex-1">
        <WorldMap disasters={state.disasters} />
      </div>
    </div>
  )
}

function DisasterChip({ disaster }: { disaster: Disaster }) {
  const { t, i18n } = useTranslation()
  const color = DISASTER_COLOR[disaster.type]
  const openCamps = disaster.camps.filter((camp) => camp.status === 'OPEN')
  const sheltered = openCamps.reduce((sum, camp) => sum + camp.population, 0)
  const statusKey = disaster.status.toLowerCase()

  return (
    <div className="flex items-center gap-3 rounded-lg border border-line bg-surface px-3.5 py-2">
      <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: color }} aria-hidden />
      <div>
        <div className="flex items-center gap-2">
          <span className="text-sm font-semibold text-ink">
            {i18n.language === 'bn' ? disaster.nameBn : disaster.nameEn}
          </span>
          <span className="font-mono text-[10px] tracking-wide text-ink-muted uppercase">
            {t(`map.disasterStatus.${statusKey}`, disaster.status)}
          </span>
        </div>
        <p className="mt-0.5 font-mono text-xs text-ink-muted">
          {t('map.campsCount', { n: openCamps.length })} ·{' '}
          {t('map.shelteredCount', { n: sheltered.toLocaleString() })}
        </p>
      </div>
    </div>
  )
}

function Centered({ children }: { children: ReactNode }) {
  return (
    <div className="flex h-full items-center justify-center p-8 text-sm text-ink-muted">{children}</div>
  )
}
