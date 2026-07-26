import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAllocationNotes } from '@/comms/useAllocationNotes'

/**
 * The case-note thread for one allocation (tier 1 of ticket 12) — the same pattern
 * `AlertWorkspace` already uses for alert notes, just attached to a different subject. Mounted
 * only once a card is expanded, so it never fires its data hook for a collapsed card.
 */
export function AllocationNoteThread({ allocationId }: { allocationId: number }) {
  const { t } = useTranslation()
  const { notes, addNote } = useAllocationNotes(allocationId)
  const [body, setBody] = useState('')

  return (
    <div className="mt-3 border-t border-line pt-3">
      <h4 className="text-xs font-semibold tracking-wide text-ink-muted uppercase">
        {t('comms.notes.title')}
      </h4>
      {!notes?.length && <p className="mt-1 text-xs text-ink-muted">{t('comms.notes.empty')}</p>}
      <ul className="mt-1 flex flex-col gap-1.5">
        {notes?.map((note, index) => (
          <li key={index} className="text-sm text-ink">
            {note.body}
          </li>
        ))}
      </ul>
      <form
        onSubmit={(event) => {
          event.preventDefault()
          if (!body.trim()) return
          void addNote(body)
          setBody('')
        }}
        className="mt-2 flex gap-2"
      >
        <input
          value={body}
          onChange={(event) => setBody(event.target.value)}
          placeholder={t('comms.notes.addPlaceholder')}
          aria-label={t('comms.notes.addPlaceholder')}
          className="flex-1 rounded border border-line bg-transparent p-2 text-sm text-ink outline-none focus-visible:border-line-strong"
        />
        <button type="submit" className="rounded-full border border-line px-3 py-1.5 text-xs text-ink-muted hover:border-line-strong hover:text-ink">
          {t('comms.notes.add')}
        </button>
      </form>
    </div>
  )
}
