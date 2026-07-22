import { Client, type IMessage } from '@stomp/stompjs'
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { getAccessToken } from '@/auth/AuthContext'

/** How often to poll when the socket is down. The fallback keeps data reasonably fresh. */
export const POLL_INTERVAL_MS = 5000

/**
 * How often a query should refetch given the socket state: never while pushed updates are
 * arriving, on an interval once they are not. This is the polling fallback in one line.
 */
export function refetchIntervalFor(connected: boolean): number | false {
  return connected ? false : POLL_INTERVAL_MS
}

interface RealtimeContextValue {
  connected: boolean
  subscribe: (destination: string, onMessage: (body: unknown) => void) => () => void
}

const RealtimeContext = createContext<RealtimeContextValue | null>(null)

function brokerUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws`
}

/**
 * Owns the single STOMP connection the whole app shares. The bearer token is presented at
 * CONNECT, which is where the server authenticates the session; each subscription is then
 * authorized server-side against the topic, so an unentitled topic simply never delivers.
 *
 * <p>Subscriptions are registered even while the socket is down and (re)applied on every
 * connect, so a dropped connection heals itself without callers needing to know.
 */
export function RealtimeProvider({ children }: { children: ReactNode }) {
  const [connected, setConnected] = useState(false)
  const clientRef = useRef<Client | null>(null)
  const handlersRef = useRef(new Map<string, Set<(body: unknown) => void>>())

  useEffect(() => {
    const client = new Client({
      brokerURL: brokerUrl(),
      connectHeaders: { Authorization: `Bearer ${getAccessToken() ?? ''}` },
      reconnectDelay: 4000,
      onConnect: () => {
        setConnected(true)
        // Re-apply every registered topic; on a reconnect the old subscriptions are gone.
        for (const destination of handlersRef.current.keys()) {
          client.subscribe(destination, (message: IMessage) => dispatch(destination, message))
        }
      },
      onDisconnect: () => setConnected(false),
      onWebSocketClose: () => setConnected(false),
      // A refused subscription arrives as an ERROR frame; the app keeps working on polling.
      onStompError: () => setConnected(false),
    })

    const dispatch = (destination: string, message: IMessage) => {
      const listeners = handlersRef.current.get(destination)
      if (!listeners) return
      const body: unknown = message.body ? JSON.parse(message.body) : null
      listeners.forEach((listener) => listener(body))
    }

    clientRef.current = client
    client.activate()
    return () => {
      clientRef.current = null
      void client.deactivate()
    }
  }, [])

  const subscribe = useCallback(
    (destination: string, onMessage: (body: unknown) => void) => {
      const handlers = handlersRef.current
      const existing = handlers.get(destination)
      const isFirst = !existing
      const listeners = existing ?? new Set<(body: unknown) => void>()
      listeners.add(onMessage)
      handlers.set(destination, listeners)

      const client = clientRef.current
      let subscription: { unsubscribe: () => void } | undefined
      if (isFirst && client?.connected) {
        subscription = client.subscribe(destination, (message: IMessage) => {
          const body: unknown = message.body ? JSON.parse(message.body) : null
          handlers.get(destination)?.forEach((listener) => listener(body))
        })
      }

      return () => {
        listeners.delete(onMessage)
        if (listeners.size === 0) {
          handlers.delete(destination)
          subscription?.unsubscribe()
        }
      }
    },
    [],
  )

  const value = useMemo(() => ({ connected, subscribe }), [connected, subscribe])
  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>
}

export function useRealtime(): RealtimeContextValue {
  const context = useContext(RealtimeContext)
  if (!context) throw new Error('useRealtime must be used within a RealtimeProvider')
  return context
}

/** Subscribe to a topic for as long as the component is mounted. */
export function useTopic(destination: string | null, onMessage: (body: unknown) => void) {
  const { subscribe, connected } = useRealtime()
  const handlerRef = useRef(onMessage)
  handlerRef.current = onMessage

  useEffect(() => {
    if (!destination) return
    return subscribe(destination, (body) => handlerRef.current(body))
  }, [destination, subscribe, connected])
}
