import { useTranslation } from 'react-i18next'
import { ArrivalStamps } from '@/family/ArrivalStamps'
import { useCampArrivals } from '@/family/useCampArrivals'
import type { CampArrivalGroup } from '@/family/api'
import { useMyCamp } from '@/world/useMyCamp'

/** Resolves the signed-in camp manager's own camp before rendering their arrivals queue. */
export function MyCampArrivalsPanel() {
  const camp = useMyCamp()
  if (!camp) return null
  return <FamilyArrivalsPanel campId={camp.id} />
}

/**
 * The camp manager's own half of dual-source arrival: every group registered for this camp, the
 * trustworthy arriving-vs-arrived counts, and the one action this role can take — confirm a
 * representative's tap. Staff may also flag a member for medical attention here; nowhere else
 * exposes that, since reunification search is deliberately blind to it.
 */
function FamilyArrivalsPanel({ campId }: { campId: number }) {
  const { t } = useTranslation()
  const { query, confirmArrival, setMedicalFlag } = useCampArrivals(campId)

  if (!query.data) return null
  const { arrivingCount, arrivedCount, groups } = query.data

  return (
    <section aria-label={t('arrivals.title')} className="border-b border-line px-4 py-4 sm:px-6">
      <div className="flex flex-wrap items-center gap-x-6 gap-y-2">
        <h2 className="font-mono text-[10px] tracking-[0.16em] text-ink-muted uppercase">{t('arrivals.title')}</h2>
        <p className="font-mono text-xs text-warn">{t('arrivals.arriving', { count: arrivingCount })}</p>
        <p className="font-mono text-xs text-ink-muted">{t('arrivals.arrived', { count: arrivedCount })}</p>
      </div>

      {groups.length === 0 ? (
        <p className="mt-3 text-sm text-ink-muted">{t('arrivals.empty')}</p>
      ) : (
        <ul className="mt-3 space-y-2">
          {groups.map((group) => (
            <GroupRow
              key={group.id}
              group={group}
              onConfirm={() => confirmArrival.mutate(group.id)}
              confirming={confirmArrival.isPending}
              onToggleMedicalFlag={(memberId, value) =>
                setMedicalFlag.mutate({ groupId: group.id, memberId, value })
              }
            />
          ))}
        </ul>
      )}
    </section>
  )
}

function GroupRow({
  group,
  onConfirm,
  confirming,
  onToggleMedicalFlag,
}: {
  group: CampArrivalGroup
  onConfirm: () => void
  confirming: boolean
  onToggleMedicalFlag: (memberId: number, value: boolean) => void
}) {
  const { t } = useTranslation()
  return (
    <li className="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-xl border border-line px-3 py-2.5">
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-ink">{group.groupName}</p>
        <p className="text-xs text-ink-muted">{t('family.memberCount', { count: group.memberCount })}</p>
      </div>

      <ArrivalStamps
        representativeArrived={group.representativeArrived}
        managerConfirmedArrived={group.managerConfirmedArrived}
      />

      <div className="flex flex-wrap gap-2">
        {group.members.map((member) => (
          <label key={member.id} className="flex items-center gap-1.5 text-xs text-ink-muted">
            <input
              type="checkbox"
              checked={member.medicalFlag}
              onChange={(e) => onToggleMedicalFlag(member.id, e.target.checked)}
              className="h-3.5 w-3.5 accent-crit"
            />
            {member.nickname}
          </label>
        ))}
      </div>

      {!group.managerConfirmedArrived && (
        <button
          type="button"
          onClick={onConfirm}
          disabled={confirming}
          className="inline-flex h-8 items-center rounded-full bg-signal px-3 text-xs font-semibold text-signal-ink transition-opacity hover:opacity-90 disabled:opacity-60"
        >
          {t('arrivals.confirm')}
        </button>
      )}
    </li>
  )
}
