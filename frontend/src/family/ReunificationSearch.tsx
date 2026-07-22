import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { searchFamilies, type ArrivalStatus } from '@/family/api'

const STATUS_COLOR: Record<ArrivalStatus, string> = {
  REGISTERED: '#94a3b8',
  ARRIVING: '#d97706',
  ARRIVED: '#16a34a',
}

/**
 * Reunification, right where someone would already be looking for a camp: search only — never
 * a directory to browse — and the result is exactly group name, camp, and status. No roster,
 * no medical data; the same public no-login surface as the camp list beside it.
 */
export function ReunificationSearch() {
  const { t, i18n } = useTranslation()
  const [query, setQuery] = useState('')
  const trimmed = query.trim()

  const results = useQuery({
    queryKey: ['family-search', trimmed],
    queryFn: () => searchFamilies(trimmed),
    enabled: trimmed.length > 1,
  })

  return (
    <div className="mt-8 border-t border-line pt-6">
      <h2 className="text-lg font-semibold">{t('reunify.title')}</h2>
      <p className="mt-1 text-sm text-ink-muted">{t('reunify.subtitle')}</p>

      <input
        type="search"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder={t('reunify.search')}
        aria-label={t('reunify.search')}
        className="mt-3 h-12 w-full rounded-xl border border-line bg-surface px-4 text-base outline-none focus-visible:border-signal focus-visible:ring-2 focus-visible:ring-signal/40"
      />

      <div className="mt-3 space-y-2" aria-live="polite">
        {trimmed.length > 1 && results.data && results.data.length === 0 && (
          <p className="text-sm text-ink-muted">{t('reunify.empty')}</p>
        )}
        {(results.data ?? []).map((result, i) => (
          <div key={i} className="rounded-xl border border-line bg-surface px-4 py-3">
            <p className="font-medium">{result.groupName}</p>
            <p className="mt-0.5 text-sm text-ink-muted">
              {i18n.language === 'bn' ? result.campNameBn : result.campNameEn}
            </p>
            <p className="mt-1 text-xs font-medium" style={{ color: STATUS_COLOR[result.status] }}>
              {t(`family.arrival.status.${result.status}`)}
            </p>
          </div>
        ))}
      </div>
    </div>
  )
}
