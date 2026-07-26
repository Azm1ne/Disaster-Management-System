import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/auth/AuthContext'
import { useBroadcastReceipts } from '@/comms/useBroadcastReceipts'
import { useBroadcasts } from '@/comms/useBroadcasts'
import type { BroadcastTargetRole, BroadcastView } from '@/comms/api'

const TARGET_ROLES: BroadcastTargetRole[] = ['CAMP_MANAGER', 'VOLUNTEER']

/**
 * Tier 2 of ticket 12. A Coordinator/Admin composes a bilingual announcement to every Camp
 * Manager or every Volunteer and can see who has read it; a Camp Manager/Volunteer sees the
 * announcements sent to their role and the act of opening one records a read receipt — the same
 * "opening it is reading it" convention as email, not a separate "mark as read" chore.
 */
export function BroadcastPanel() {
  const { t, i18n } = useTranslation()
  const { user } = useAuth()
  const { broadcasts, send, markRead } = useBroadcasts()
  const isSender = user?.role === 'COORDINATOR' || user?.role === 'ADMIN'
  const [expandedId, setExpandedId] = useState<number | null>(null)

  if (!broadcasts) {
    return null
  }

  return (
    <section className="flex flex-col gap-4 p-4 sm:p-6">
      <div>
        <h2 className="text-lg font-semibold text-ink">{t('comms.broadcasts.title')}</h2>
        <p className="text-sm text-ink-muted">
          {isSender ? t('comms.broadcasts.subtitleSender') : t('comms.broadcasts.subtitleRecipient')}
        </p>
      </div>

      {isSender && <ComposeBroadcast onSend={send} />}

      {broadcasts.length === 0 ? (
        <p className="text-sm text-ink-muted">{t('comms.broadcasts.empty')}</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {broadcasts.map((broadcast) => (
            <BroadcastRow
              key={broadcast.id}
              broadcast={broadcast}
              isSender={isSender}
              expanded={expandedId === broadcast.id}
              onToggle={() => {
                const nowOpen = expandedId !== broadcast.id
                setExpandedId(nowOpen ? broadcast.id : null)
                if (nowOpen && !isSender) {
                  void markRead(broadcast.id)
                }
              }}
              language={i18n.language}
            />
          ))}
        </ul>
      )}
    </section>
  )
}

function ComposeBroadcast({
  onSend,
}: {
  onSend: (input: { targetRole: BroadcastTargetRole; bodyEn: string; bodyBn: string }) => Promise<void>
}) {
  const { t } = useTranslation()
  const [targetRole, setTargetRole] = useState<BroadcastTargetRole>('CAMP_MANAGER')
  const [bodyEn, setBodyEn] = useState('')
  const [bodyBn, setBodyBn] = useState('')

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        if (!bodyEn.trim() || !bodyBn.trim()) return
        void onSend({ targetRole, bodyEn, bodyBn }).then(() => {
          setBodyEn('')
          setBodyBn('')
        })
      }}
      className="flex flex-col gap-2 rounded-lg border border-line p-3"
    >
      <select
        value={targetRole}
        onChange={(event) => setTargetRole(event.target.value as BroadcastTargetRole)}
        aria-label={t('comms.broadcasts.targetLabel')}
        className="rounded border border-line bg-transparent p-2 text-sm text-ink"
      >
        {TARGET_ROLES.map((role) => (
          <option key={role} value={role}>
            {t(`comms.broadcasts.target.${role}`)}
          </option>
        ))}
      </select>
      <textarea
        value={bodyEn}
        onChange={(event) => setBodyEn(event.target.value)}
        placeholder={t('comms.broadcasts.bodyEnPlaceholder')}
        aria-label={t('comms.broadcasts.bodyEnPlaceholder')}
        className="rounded border border-line bg-transparent p-2 text-sm text-ink"
      />
      <textarea
        value={bodyBn}
        onChange={(event) => setBodyBn(event.target.value)}
        placeholder={t('comms.broadcasts.bodyBnPlaceholder')}
        aria-label={t('comms.broadcasts.bodyBnPlaceholder')}
        lang="bn"
        className="rounded border border-line bg-transparent p-2 text-sm text-ink"
      />
      <button
        type="submit"
        className="self-start rounded-full bg-signal px-4 py-1.5 text-xs font-semibold text-signal-ink transition-opacity hover:opacity-90"
      >
        {t('comms.broadcasts.send')}
      </button>
    </form>
  )
}

function BroadcastRow({
  broadcast,
  isSender,
  expanded,
  onToggle,
  language,
}: {
  broadcast: BroadcastView
  isSender: boolean
  expanded: boolean
  onToggle: () => void
  language: string
}) {
  const { t } = useTranslation()
  const body = language === 'bn' ? broadcast.bodyBn : broadcast.bodyEn
  const receipts = useBroadcastReceipts(expanded && isSender ? broadcast.id : null)

  return (
    <li className="rounded-lg border border-line p-3">
      <button type="button" onClick={onToggle} className="flex w-full items-start justify-between gap-3 text-left">
        <div>
          <p className="text-sm text-ink">{body}</p>
          <p className="mt-1 font-mono text-[11px] text-ink-muted">
            {isSender
              ? t(`comms.broadcasts.target.${broadcast.targetRole}`)
              : new Date(broadcast.createdAt).toLocaleString(language)}
          </p>
        </div>
        {isSender && (
          <span className="shrink-0 rounded-full border border-line px-2 py-0.5 font-mono text-[11px] text-ink-muted">
            {t('comms.broadcasts.readCount', { count: receipts?.length ?? 0 })}
          </span>
        )}
      </button>
      {expanded && isSender && (
        <ul className="mt-2 flex flex-col gap-1 border-t border-line pt-2">
          {!receipts?.length && <li className="text-xs text-ink-muted">{t('comms.broadcasts.noReadsYet')}</li>}
          {receipts?.map((receipt) => (
            <li key={receipt.userId} className="font-mono text-[11px] text-ink-muted">
              #{receipt.userId} · {new Date(receipt.readAt).toLocaleString(language)}
            </li>
          ))}
        </ul>
      )}
    </li>
  )
}
