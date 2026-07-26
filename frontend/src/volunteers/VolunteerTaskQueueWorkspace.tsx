import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAssignVolunteer, useCandidates, useSkillGap, useVolunteerTasks } from '@/volunteers/useVolunteers'
import type { Skill, VolunteerCandidate, VolunteerTaskSummary } from '@/volunteers/api'

const STATUS_TONE: Record<VolunteerTaskSummary['status'], string> = {
  OPEN: 'text-ink-muted',
  ASSIGNED: 'text-ok',
  CANCELLED: 'text-crit',
}

/** The coordinator's volunteer matching surface: the skill-coverage gap strip up top (what's
 * short right now, at a glance), then the task queue — one card per shift, expandable into its
 * ranked candidate list for a one-click push-assign. Camp Manager has no place here (the backend
 * restricts push-assign/candidates to Coordinator/Admin, matching the allocation queue's
 * oversight-only convention), so this workspace is only ever mounted for those two roles. */
export function VolunteerTaskQueueWorkspace() {
  const { t } = useTranslation()
  const tasks = useVolunteerTasks()
  const [expandedTaskId, setExpandedTaskId] = useState<number | null>(null)

  if (!tasks) {
    return null
  }

  return (
    <section className="flex flex-col gap-4 p-4 sm:p-6">
      <div>
        <h2 className="text-lg font-semibold text-ink">{t('volunteers.title')}</h2>
        <p className="text-sm text-ink-muted">{t('volunteers.subtitle')}</p>
      </div>

      <SkillGapStrip />

      {tasks.length === 0 ? (
        <p className="text-sm text-ink-muted">{t('volunteers.empty')}</p>
      ) : (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
          {tasks.map((task) => (
            <TaskCard
              key={task.id}
              task={task}
              expanded={expandedTaskId === task.id}
              onToggle={() => setExpandedTaskId(expandedTaskId === task.id ? null : task.id)}
            />
          ))}
        </div>
      )}
    </section>
  )
}

function SkillGapStrip() {
  const { t } = useTranslation()
  const coverage = useSkillGap()
  if (!coverage || coverage.length === 0) {
    return null
  }
  return (
    <div className="flex flex-wrap gap-2 rounded-lg border border-line bg-surface p-3">
      <span className="self-center text-xs font-semibold tracking-wide text-ink-muted uppercase">
        {t('volunteers.skillGap')}
      </span>
      {coverage.map((c) => (
        <span
          key={c.skill}
          className={
            c.unmet
              ? 'inline-flex items-center gap-1.5 rounded-full border border-crit/40 bg-crit/10 px-2.5 py-1 text-xs font-medium text-crit'
              : 'inline-flex items-center gap-1.5 rounded-full border border-line px-2.5 py-1 text-xs text-ink-muted'
          }
        >
          {t(`volunteers.skill.${c.skill}`)}
          <span className="font-mono tabular-nums">
            {c.availableVolunteerCount}/{c.openTaskCount}
          </span>
        </span>
      ))}
    </div>
  )
}

function TaskCard({
  task,
  expanded,
  onToggle,
}: {
  task: VolunteerTaskSummary
  expanded: boolean
  onToggle: () => void
}) {
  const { t } = useTranslation()
  return (
    <div data-task-id={task.id} className="rounded-lg border border-line bg-surface p-4">
      <div className="flex items-center justify-between">
        <span className="inline-flex items-center rounded-full bg-surface-2 px-2.5 py-0.5 text-xs font-medium text-signal">
          {t(`volunteers.skill.${task.requiredSkill}`)}
        </span>
        <span className={`text-xs font-medium ${STATUS_TONE[task.status]}`}>
          {t(`volunteers.statusValue.${task.status}`)}
        </span>
      </div>

      <p className="mt-2 text-sm text-ink">{task.description}</p>

      <div className="mt-2 flex items-center justify-between">
        <span className="text-xs text-ink-muted">
          {t('volunteers.urgency')}: <span className="font-mono tabular-nums">{task.urgencyScore.toFixed(2)}</span>
        </span>
        {task.status === 'ASSIGNED' && (
          <span className="text-xs text-ink-muted">
            {t('volunteers.assignedVia', {
              name: task.assignedVolunteerNameEn,
              method: t(`volunteers.method.${task.assignmentMethod}`),
            })}
          </span>
        )}
      </div>

      {task.canAssign && (
        <button
          type="button"
          onClick={onToggle}
          className="mt-3 inline-flex h-8 items-center rounded-full border border-line px-3 text-xs text-ink-muted transition-colors hover:border-line-strong hover:text-ink"
        >
          {expanded ? t('volunteers.hideCandidates') : t('volunteers.showCandidates')}
        </button>
      )}

      {expanded && task.canAssign && <CandidateList taskId={task.id} requiredSkill={task.requiredSkill} />}
    </div>
  )
}

function CandidateList({ taskId, requiredSkill }: { taskId: number; requiredSkill: Skill }) {
  const { t } = useTranslation()
  const candidates = useCandidates(taskId)
  const assign = useAssignVolunteer()
  const [assigningId, setAssigningId] = useState<number | null>(null)

  if (!candidates) {
    return <p className="mt-3 text-xs text-ink-muted">{t('volunteers.loadingCandidates')}</p>
  }

  return (
    <ul className="mt-3 flex flex-col gap-2 border-t border-line pt-3">
      {candidates.map((candidate, index) => (
        <CandidateRow
          key={candidate.volunteerId}
          candidate={candidate}
          rank={index + 1}
          requiredSkill={requiredSkill}
          busy={assigningId === candidate.volunteerId}
          onAssign={async () => {
            setAssigningId(candidate.volunteerId)
            try {
              await assign(taskId, candidate.volunteerId)
            } finally {
              setAssigningId(null)
            }
          }}
        />
      ))}
    </ul>
  )
}

function CandidateRow({
  candidate,
  rank,
  requiredSkill,
  busy,
  onAssign,
}: {
  candidate: VolunteerCandidate
  rank: number
  requiredSkill: Skill
  busy: boolean
  onAssign: () => void
}) {
  const { t } = useTranslation()
  const name = t('volunteers.candidateName', { name: candidate.nameEn })

  return (
    <li className="flex items-center gap-3 rounded-md bg-surface-2 px-3 py-2">
      <span className="w-5 shrink-0 text-center font-mono text-[11px] text-ink-muted">{rank}</span>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-ink">{name}</p>
        <p className="text-[11px] text-ink-muted">
          {candidate.hasSkill
            ? t('volunteers.hasSkill', { skill: t(`volunteers.skill.${requiredSkill}`) })
            : t('volunteers.missingSkill')}
          {' · '}
          {t('volunteers.distanceAway', { km: candidate.distanceKm.toFixed(1) })}
        </p>
      </div>
      <span className="font-mono text-xs tabular-nums text-ink-muted">{candidate.score.toFixed(2)}</span>
      <button
        type="button"
        disabled={busy}
        onClick={onAssign}
        className="inline-flex h-7 shrink-0 items-center rounded-full bg-signal px-3 text-[11px] font-semibold text-signal-ink transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-40"
      >
        {t('volunteers.assign')}
      </button>
    </li>
  )
}
