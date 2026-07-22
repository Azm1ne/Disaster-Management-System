import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { ArrivalStamps } from '@/family/ArrivalStamps'
import { useMyFamilyGroup } from '@/family/useMyFamilyGroup'
import type { AgeBand, MemberInput } from '@/family/api'
import { useDisasters } from '@/world/useDisasters'

const AGE_BANDS: AgeBand[] = ['ADULT', 'CHILD', 'ELDER']

/**
 * A victim's own registration and status, in one place: register the household once (a solo
 * victim just leaves the member list at one row), then watch the same two-stamp arrival this
 * ticket is built around. There is nothing else for this role to do here, so no tabs or routing.
 */
export function FamilyPanel() {
  const { query, register, confirmArrival } = useMyFamilyGroup()

  if (query.isPending) return null
  if (query.isError) return <ErrorState />
  return query.data ? (
    <StatusView group={query.data} onConfirmArrival={() => confirmArrival.mutate()} confirming={confirmArrival.isPending} />
  ) : (
    <RegistrationForm onSubmit={(request) => register.mutate(request)} submitting={register.isPending} failed={register.isError} />
  )
}

function ErrorState() {
  const { t } = useTranslation()
  return <p className="mt-8 text-ink-muted">{t('family.error')}</p>
}

function StatusView({
  group,
  onConfirmArrival,
  confirming,
}: {
  group: NonNullable<ReturnType<typeof useMyFamilyGroup>['query']['data']>
  onConfirmArrival: () => void
  confirming: boolean
}) {
  const { t, i18n } = useTranslation()
  const campName = i18n.language === 'bn' ? group.campNameBn : group.campNameEn

  return (
    <div className="mt-8 rounded-2xl border border-line bg-surface p-6 sm:p-8">
      <span className="inline-flex items-center rounded-full bg-surface-2 px-3 py-1 text-sm font-medium text-signal">
        {t('family.registered')}
      </span>
      <h2 className="mt-4 text-2xl font-bold tracking-tight">{group.groupName}</h2>
      <p className="mt-1 text-ink-muted">{t('family.atCamp', { camp: campName })}</p>
      <p className="mt-1 text-ink-muted">{t('family.memberCount', { count: group.memberCount })}</p>

      <div className="mt-6 flex flex-wrap items-center gap-4 border-t border-line pt-6">
        <ArrivalStamps
          representativeArrived={group.representativeArrived}
          managerConfirmedArrived={group.managerConfirmedArrived}
        />
        <p className="font-medium">{t(`family.arrival.status.${group.status}`)}</p>
      </div>

      {!group.representativeArrived && (
        <button
          type="button"
          onClick={onConfirmArrival}
          disabled={confirming}
          className="mt-6 h-12 w-full rounded-xl bg-signal text-base font-semibold text-signal-ink transition-opacity hover:opacity-90 disabled:opacity-60 sm:w-auto sm:px-8"
        >
          {confirming ? t('family.arrival.confirming') : t('family.arrival.iHaveArrived')}
        </button>
      )}
    </div>
  )
}

function RegistrationForm({
  onSubmit,
  submitting,
  failed,
}: {
  onSubmit: (request: { campId: number; groupName: string; members: MemberInput[] }) => void
  submitting: boolean
  failed: boolean
}) {
  const { t, i18n } = useTranslation()
  const disastersState = useDisasters()
  const [groupName, setGroupName] = useState('')
  const [campId, setCampId] = useState<number | ''>('')
  const [members, setMembers] = useState<MemberInput[]>([{ nickname: '', ageBand: 'ADULT' }])

  const openCamps =
    disastersState.status === 'ready'
      ? disastersState.disasters.flatMap((d) => d.camps.filter((c) => c.status === 'OPEN'))
      : []

  function updateMember(index: number, patch: Partial<MemberInput>) {
    setMembers((prev) => prev.map((m, i) => (i === index ? { ...m, ...patch } : m)))
  }

  function addMember() {
    setMembers((prev) => [...prev, { nickname: '', ageBand: 'ADULT' }])
  }

  function removeMember(index: number) {
    setMembers((prev) => prev.filter((_, i) => i !== index))
  }

  const canSubmit =
    groupName.trim().length > 0 && campId !== '' && members.every((m) => m.nickname.trim().length > 0)

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!canSubmit) return
    onSubmit({ campId: campId as number, groupName: groupName.trim(), members })
  }

  return (
    <form onSubmit={handleSubmit} className="mt-8 rounded-2xl border border-line bg-surface p-6 sm:p-8">
      <h2 className="text-xl font-semibold">{t('family.register.title')}</h2>
      <p className="mt-2 text-ink-muted">{t('family.register.subtitle')}</p>

      <label className="mt-6 block text-sm font-medium text-ink-muted">
        {t('family.register.groupName')}
        <input
          type="text"
          value={groupName}
          onChange={(e) => setGroupName(e.target.value)}
          placeholder={t('family.register.groupNamePlaceholder')}
          className="mt-1.5 h-12 w-full rounded-xl border border-line bg-bg px-4 text-base text-ink outline-none focus-visible:border-signal focus-visible:ring-2 focus-visible:ring-signal/40"
        />
      </label>

      <label className="mt-4 block text-sm font-medium text-ink-muted">
        {t('family.register.camp')}
        <select
          value={campId}
          onChange={(e) => setCampId(e.target.value ? Number(e.target.value) : '')}
          className="mt-1.5 h-12 w-full rounded-xl border border-line bg-bg px-4 text-base text-ink outline-none focus-visible:border-signal focus-visible:ring-2 focus-visible:ring-signal/40"
        >
          <option value="">{t('family.register.campPlaceholder')}</option>
          {openCamps.map((camp) => (
            <option key={camp.id} value={camp.id}>
              {i18n.language === 'bn' ? camp.nameBn : camp.nameEn}
            </option>
          ))}
        </select>
      </label>

      <div className="mt-6 border-t border-line pt-5">
        <p className="text-sm font-medium text-ink-muted">{t('family.register.members')}</p>
        <div className="mt-3 space-y-3">
          {members.map((member, index) => (
            <div key={index} className="flex items-center gap-2">
              <input
                type="text"
                value={member.nickname}
                onChange={(e) => updateMember(index, { nickname: e.target.value })}
                placeholder={t('family.register.nicknamePlaceholder')}
                className="h-11 flex-1 rounded-xl border border-line bg-bg px-3 text-sm text-ink outline-none focus-visible:border-signal focus-visible:ring-2 focus-visible:ring-signal/40"
              />
              <select
                value={member.ageBand}
                onChange={(e) => updateMember(index, { ageBand: e.target.value as AgeBand })}
                className="h-11 rounded-xl border border-line bg-bg px-2 text-sm text-ink outline-none focus-visible:border-signal focus-visible:ring-2 focus-visible:ring-signal/40"
              >
                {AGE_BANDS.map((band) => (
                  <option key={band} value={band}>
                    {t(`family.ageBand.${band}`)}
                  </option>
                ))}
              </select>
              {members.length > 1 && (
                <button
                  type="button"
                  onClick={() => removeMember(index)}
                  aria-label={t('family.register.removeMember')}
                  className="h-11 w-11 shrink-0 rounded-xl border border-line text-ink-muted transition-colors hover:border-crit hover:text-crit"
                >
                  ✕
                </button>
              )}
            </div>
          ))}
        </div>
        <button
          type="button"
          onClick={addMember}
          className="mt-3 text-sm font-medium text-signal hover:opacity-80"
        >
          {t('family.register.addMember')}
        </button>
      </div>

      {failed && <p className="mt-4 text-sm text-crit">{t('family.register.error')}</p>}

      <button
        type="submit"
        disabled={!canSubmit || submitting}
        className="mt-6 h-12 w-full rounded-xl bg-signal text-base font-semibold text-signal-ink transition-opacity hover:opacity-90 disabled:opacity-60 sm:w-auto sm:px-8"
      >
        {submitting ? t('family.register.submitting') : t('family.register.submit')}
      </button>
    </form>
  )
}
