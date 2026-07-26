import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useDmContacts, useDmThread, useMyUserId } from '@/comms/useDm'
import type { ContactView } from '@/comms/api'

/**
 * Tier 3 of ticket 12: narrow 1:1 messaging, but only along a real operational relationship —
 * the contact list itself is server-derived (`DmRelationshipService.contactsFor`), so there is
 * no free-text "message anyone" affordance to begin with; refusal is enforced again server-side
 * regardless.
 */
export function DmPanel() {
  const { t, i18n } = useTranslation()
  const contacts = useDmContacts()
  const myUserId = useMyUserId()
  const [selected, setSelected] = useState<ContactView | null>(null)
  const { messages, send } = useDmThread(selected?.userId ?? null)
  const [body, setBody] = useState('')

  if (!contacts) {
    return null
  }

  return (
    <section className="flex min-h-0 flex-1 flex-col gap-4 p-4 sm:p-6">
      <div>
        <h2 className="text-lg font-semibold text-ink">{t('comms.dm.title')}</h2>
        <p className="text-sm text-ink-muted">{t('comms.dm.subtitle')}</p>
      </div>

      {contacts.length === 0 ? (
        <p className="text-sm text-ink-muted">{t('comms.dm.noContacts')}</p>
      ) : (
        <div className="flex min-h-0 flex-1 gap-4">
          <ul className="flex w-48 shrink-0 flex-col gap-1">
            {contacts.map((contact) => (
              <li key={contact.userId}>
                <button
                  type="button"
                  onClick={() => setSelected(contact)}
                  className={
                    selected?.userId === contact.userId
                      ? 'w-full rounded-md bg-surface-2 px-3 py-2 text-left text-sm font-medium text-ink'
                      : 'w-full rounded-md px-3 py-2 text-left text-sm text-ink-muted hover:text-ink'
                  }
                >
                  <span className="block truncate">
                    {i18n.language === 'bn' ? contact.nameBn : contact.nameEn}
                  </span>
                  <span className="block font-mono text-[10px] text-ink-muted">
                    {t(`roles.${contact.role.toLowerCase()}`)}
                  </span>
                </button>
              </li>
            ))}
          </ul>

          {selected ? (
            <div className="flex min-h-0 flex-1 flex-col rounded-lg border border-line">
              <ul className="flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto p-3">
                {!messages?.length && <li className="text-sm text-ink-muted">{t('comms.dm.empty')}</li>}
                {messages?.map((message) => {
                  const mine = message.senderUserId === myUserId
                  return (
                    <li key={message.id} className={mine ? 'self-end text-right' : 'self-start text-left'}>
                      <p
                        className={
                          mine
                            ? 'inline-block max-w-xs rounded-md border border-signal/40 bg-signal/10 px-3 py-1.5 text-sm text-ink'
                            : 'inline-block max-w-xs rounded-md border border-line px-3 py-1.5 text-sm text-ink'
                        }
                      >
                        {message.body}
                      </p>
                      <span className="mt-0.5 block font-mono text-[10px] text-ink-muted">
                        {new Date(message.createdAt).toLocaleTimeString(i18n.language)}
                      </span>
                    </li>
                  )
                })}
              </ul>
              <form
                onSubmit={(event) => {
                  event.preventDefault()
                  if (!body.trim()) return
                  void send(body)
                  setBody('')
                }}
                className="flex gap-2 border-t border-line p-2"
              >
                <input
                  value={body}
                  onChange={(event) => setBody(event.target.value)}
                  placeholder={t('comms.dm.placeholder')}
                  aria-label={t('comms.dm.placeholder')}
                  className="flex-1 rounded border border-line bg-transparent p-2 text-sm text-ink outline-none focus-visible:border-line-strong"
                />
                <button type="submit" className="rounded-full border border-line px-3 py-1.5 text-xs text-ink-muted hover:border-line-strong hover:text-ink">
                  {t('comms.dm.send')}
                </button>
              </form>
            </div>
          ) : (
            <p className="flex flex-1 items-center justify-center text-sm text-ink-muted">
              {t('comms.dm.pickContact')}
            </p>
          )}
        </div>
      )}
    </section>
  )
}
