import { useQuery } from '@tanstack/react-query'
import { useAuth } from '@/auth/AuthContext'
import { refetchIntervalFor, useRealtime } from '@/realtime/RealtimeProvider'
import { fetchForecasts, type ForecastView } from '@/forecasts/api'

/** Every camp/resource forecast, polled on the same cadence as the realtime fallback (there is
 * no dedicated forecast STOMP topic — ticks change resources fast enough that a 5s poll while
 * disconnected, or none while connected and the world topic already refetches this, is enough). */
export function useForecasts(): ForecastView[] | undefined {
  const { authFetch } = useAuth()
  const { connected } = useRealtime()

  const forecasts = useQuery({
    queryKey: ['forecasts'],
    queryFn: () => fetchForecasts(authFetch),
    refetchInterval: refetchIntervalFor(connected),
  })

  return forecasts.data
}
