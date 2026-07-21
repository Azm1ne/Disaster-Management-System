import { useTranslation } from 'react-i18next'
import { useMyCamp } from '@/world/useMyCamp'

/**
 * A Camp Manager's own camp, live. It arrives on the camp's own realtime topic — the one thing
 * this role is entitled to beyond the shared picture — so occupancy and stock move as the
 * simulation ticks. Occupancy is the headline because a camp over its capacity is the fact a
 * manager must act on first.
 */
export function MyCampPanel() {
  const { t, i18n } = useTranslation()
  const camp = useMyCamp()

  if (!camp) return null

  const name = i18n.language === 'bn' ? camp.nameBn : camp.nameEn
  const occupancy = camp.capacity > 0 ? camp.population / camp.capacity : 0
  const over = camp.population > camp.capacity

  return (
    <section
      aria-label={t('camp.mine')}
      className="flex flex-wrap items-center gap-x-6 gap-y-3 border-b border-line px-4 py-3 sm:px-6"
    >
      <div className="min-w-0">
        <p className="font-mono text-[10px] tracking-[0.16em] text-ink-muted uppercase">
          {t('camp.mine')}
        </p>
        <p className="truncate text-sm font-semibold text-ink">{name}</p>
      </div>

      <div className="min-w-40">
        <p className="flex items-baseline gap-2 font-mono text-xs text-ink-muted">
          <span className={`tabular-nums ${over ? 'text-crit' : 'text-ink'}`}>
            {camp.population.toLocaleString(i18n.language)} / {camp.capacity.toLocaleString(i18n.language)}
          </span>
          <span>{over ? t('camp.overCapacity') : t('camp.occupancy')}</span>
        </p>
        <div className="mt-1.5 h-1 w-full overflow-hidden rounded-full bg-surface-2">
          <div
            className={`h-full rounded-full transition-[width] duration-500 ease-out ${over ? 'bg-crit' : 'bg-signal'}`}
            style={{ width: `${Math.min(100, occupancy * 100)}%` }}
          />
        </div>
      </div>

      <ul className="flex flex-wrap gap-2">
        {camp.resources.map((resource) => (
          <li
            key={resource.type}
            className="rounded-md border border-line px-2.5 py-1 font-mono text-[11px] text-ink-muted"
          >
            <span className="text-ink-muted">{t(`camp.resource.${resource.type}`)}</span>{' '}
            <span className="tabular-nums text-ink">
              {Math.round(resource.quantity).toLocaleString(i18n.language)}
            </span>
          </li>
        ))}
      </ul>
    </section>
  )
}
