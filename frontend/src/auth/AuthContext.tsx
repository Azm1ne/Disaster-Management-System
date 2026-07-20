import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from 'react'

export interface AuthUser {
  username: string
  role: string
  nameEn: string
  nameBn: string
}

interface AuthResponse extends AuthUser {
  accessToken: string
  refreshToken: string
}

interface AuthContextValue {
  user: AuthUser | null
  login: (username: string, password: string) => Promise<AuthUser>
  logout: () => void
  /** fetch() with the bearer token attached and one transparent refresh-on-401 retry. */
  authFetch: (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>
}

const STORAGE = { access: 'dms.access', refresh: 'dms.refresh', user: 'dms.user' }
const JSON_HEADERS = { 'Content-Type': 'application/json' }

function readStoredUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem(STORAGE.user)
    return raw ? (JSON.parse(raw) as AuthUser) : null
  } catch {
    return null
  }
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(readStoredUser)

  const persist = useCallback((response: AuthResponse): AuthUser => {
    const nextUser: AuthUser = {
      username: response.username,
      role: response.role,
      nameEn: response.nameEn,
      nameBn: response.nameBn,
    }
    localStorage.setItem(STORAGE.access, response.accessToken)
    localStorage.setItem(STORAGE.refresh, response.refreshToken)
    localStorage.setItem(STORAGE.user, JSON.stringify(nextUser))
    setUser(nextUser)
    return nextUser
  }, [])

  const clear = useCallback(() => {
    localStorage.removeItem(STORAGE.access)
    localStorage.removeItem(STORAGE.refresh)
    localStorage.removeItem(STORAGE.user)
    setUser(null)
  }, [])

  const login = useCallback(
    async (username: string, password: string) => {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: JSON_HEADERS,
        body: JSON.stringify({ username, password }),
      })
      if (!response.ok) throw new Error('login_failed')
      return persist((await response.json()) as AuthResponse)
    },
    [persist],
  )

  const logout = useCallback(() => {
    const refreshToken = localStorage.getItem(STORAGE.refresh)
    if (refreshToken) {
      // Best-effort revoke; the local session is cleared regardless of the result.
      void fetch('/api/auth/logout', {
        method: 'POST',
        headers: JSON_HEADERS,
        body: JSON.stringify({ refreshToken }),
      })
    }
    clear()
  }, [clear])

  const authFetch = useCallback(
    async (input: RequestInfo | URL, init: RequestInit = {}) => {
      const withBearer = (token: string): RequestInit => ({
        ...init,
        headers: { ...init.headers, Authorization: `Bearer ${token}` },
      })

      const response = await fetch(input, withBearer(localStorage.getItem(STORAGE.access) ?? ''))
      if (response.status !== 401) return response

      const refreshToken = localStorage.getItem(STORAGE.refresh)
      if (!refreshToken) {
        clear()
        return response
      }
      const refreshed = await fetch('/api/auth/refresh', {
        method: 'POST',
        headers: JSON_HEADERS,
        body: JSON.stringify({ refreshToken }),
      })
      if (!refreshed.ok) {
        clear()
        return response
      }
      persist((await refreshed.json()) as AuthResponse)
      return fetch(input, withBearer(localStorage.getItem(STORAGE.access) ?? ''))
    },
    [clear, persist],
  )

  const value = useMemo(
    () => ({ user, login, logout, authFetch }),
    [user, login, logout, authFetch],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within an AuthProvider')
  return context
}
