import { useTranslation } from 'react-i18next'
import { useForecasts } from '@/forecasts/useForecasts'
import type { ConfidenceLevel, ForecastView } from '@/forecasts/api'

const CONFIDENCE_KEY: Record<ConfidenceLevel, string> = {
  HIGH: 'forecasts.confidenceHigh',
  MEDIUM: 'forecasts.confidenceMedium',
  LOW: 'forecasts.confidenceLow',
}

const CONFIDENCE_TONE: Record<ConfidenceLevel, string> = {
  HIGH: 'text-ok',
  MEDIUM: 'text-warn',
  LOW: 'text-crit',
}

/** The dedicated forecasts screen: one card per camp/resource, showing the explainable
 * breakdown (current quantity, rate, time-to-exhaustion, confidence band and its inputs) —
 * never just a bare number. */
export function ForecastWorkspace() {
  const { t, i18n } = useTranslation()
  const forecasts = useForecasts()

  if (!forecasts) {
    return null
  }

  return (
    <section className="flex flex-col gap-4 p-4 sm:p-6">
      <div>
        <h2 className="text-lg font-semibold text-ink">{t('forecasts.title')}</h2>
        <p className="text-sm text-ink-muted">{t('forecasts.subtitle')}</p>
      </div>
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
        {forecasts.map((f) => (
          <ForecastCard key={`${f.campId}-${f.resourceType}`} forecast={f} lang={i18n.language} />
        ))}
      </div>
    </section>
  )
}

function ForecastCard({ forecast, lang }: { forecast: ForecastView; lang: string }) {
  const { t, i18n } = useTranslation()
  const campName = lang === 'bn' ? forecast.campNameBn : forecast.campNameEn
  const resourceLabel = t(`camp.resource.${forecast.resourceType}`)

  return (
    <div className="rounded-lg border border-line bg-surface p-4">
      <div className="flex items-center justify-between">
        <p className="text-sm font-medium text-ink">{campName}</p>
        <span className="text-xs text-ink-muted">{resourceLabel}</span>
      </div>

      <div className="mt-3 space-y-1 text-sm">
        <p>
          {t('forecasts.rate')}: {forecast.ratePerTick.toLocaleString(i18n.language)} {t('forecasts.perTick')}
        </p>
        {forecast.ticksRemainingEstimate === null ? (
          <p className="text-ink-muted">{t('forecasts.stable')}</p>
        ) : (
          <>
            <p>
              {t('forecasts.ticksRemaining')} ({t('forecasts.estimate')}):{' '}
              {forecast.ticksRemainingEstimate}
            </p>
            <p className="text-ink-muted">
              {t('forecasts.range')}: {forecast.ticksRemainingWorstCase}–{forecast.ticksRemainingBestCase}
            </p>
          </>
        )}
        <p className={CONFIDENCE_TONE[forecast.confidenceLevel]}>
          {t('forecasts.confidence')}: {t(CONFIDENCE_KEY[forecast.confidenceLevel])}
        </p>
      </div>

      <p className="mt-3 text-xs text-ink-muted">
        {t('forecasts.inputs')} — {t('forecasts.latestReading', { tick: forecast.latestObservedTick })},{' '}
        {t('forecasts.sampleCount', { count: forecast.sampleCount })}
      </p>
    </div>
  )
}
