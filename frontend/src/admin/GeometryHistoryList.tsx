import { useTranslation } from 'react-i18next'
import { useGeometryHistory } from '@/admin/useAdminDisasters'
import type { GeometrySubjectType } from '@/admin/api'

/**
 * The geometry-edit trail for one disaster or affected area, read straight off
 * `GeometryHistory` — an append-only ledger, so it renders as a timeline (oldest first, a
 * connecting rule between entries) rather than a table, matching what the data actually is.
 * There's no user-lookup endpoint wired to this slice, so the actor is shown as a plain
 * "User #N" — a deliberate minimal choice, not an oversight (see Task 7 report).
 */
export function GeometryHistoryList({
  subjectType,
  subjectId,
}: {
  subjectType: GeometrySubjectType
  subjectId: number
}) {
  const { t, i18n } = useTranslation()
  const { data, isPending } = useGeometryHistory(subjectType, subjectId)

  if (isPending) return null
  if (!data || data.length === 0) {
    return <p className="text-sm text-ink-muted">{t('admin.history.empty')}</p>
  }

  return (
    <ol className="relative flex flex-col gap-4 border-l border-line pl-5">
      {data.map((entry) => (
        <li key={entry.id} className="relative">
          <span className="absolute top-1 -left-[23px] h-2 w-2 rounded-full bg-signal" aria-hidden />
          <p className="font-mono text-[11px] tracking-wide text-ink-muted uppercase">
            {new Date(entry.createdAt).toLocaleString(i18n.language)}
          </p>
          <p className="mt-0.5 text-sm text-ink">
            {entry.previousGeometry === null ? t('admin.history.entryCreated') : t('admin.history.entryUpdated')}
          </p>
          <p className="mt-0.5 text-xs text-ink-muted">
            {t('admin.history.actor', { id: entry.actorUserId })} ·{' '}
            {t('admin.history.vertexCount', { count: entry.newGeometry.coordinates[0].length })}
          </p>
        </li>
      ))}
    </ol>
  )
}
