import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '@/auth/AuthContext'
import { LanguageToggle } from '@/components/LanguageToggle'
import { ROLES, homePathForRole } from '@/roles'

const DEMO_PASSWORD = 'relief2026'

/**
 * The front door of the command system. It carries the signature of the whole app: a
 * bilingual lockup over a faint braided-channel motif of the Jamuna river — the real place
 * this operation responds to. Rendered in the operator (dark) theme regardless of who signs
 * in; each role is handed to its own light or dark shell afterwards.
 */
export default function Login() {
  const { t } = useTranslation()
  const { user, login } = useAuth()
  const navigate = useNavigate()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(false)
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to={homePathForRole(user.role)} replace />

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(false)
    try {
      const signedIn = await login(username.trim(), password)
      navigate(homePathForRole(signedIn.role), { replace: true })
    } catch {
      setError(true)
      setBusy(false)
    }
  }

  function fillDemo(demoUsername: string) {
    setUsername(demoUsername)
    setPassword(DEMO_PASSWORD)
    setError(false)
  }

  return (
    <div data-theme="operator" className="relative min-h-svh overflow-hidden bg-bg text-ink">
      <RiverContour />

      <header className="relative z-10 flex items-center justify-between px-6 py-5 sm:px-10">
        <div className="flex items-center gap-2.5">
          <Mark />
          <span className="font-mono text-xs tracking-[0.2em] text-ink-muted uppercase">
            {t('appNameShort')}
          </span>
        </div>
        <LanguageToggle />
      </header>

      <main className="relative z-10 mx-auto grid max-w-5xl gap-10 px-6 pt-4 pb-16 sm:px-10 md:grid-cols-[1.1fr_1fr] md:items-center md:gap-16 md:pt-10">
        <section className="max-w-md">
          <p className="font-mono text-xs tracking-[0.18em] text-signal uppercase">
            {t('login.eyebrow')}
          </p>
          <h1 className="mt-4 text-4xl leading-tight font-extrabold tracking-tight sm:text-5xl">
            {t('appName')}
          </h1>
          <p className="mt-4 text-base text-ink-muted">{t('login.subtitle')}</p>
        </section>

        <section className="w-full max-w-md rounded-2xl border border-line bg-surface/70 p-6 backdrop-blur-sm sm:p-8">
          <h2 className="text-lg font-semibold">{t('login.title')}</h2>

          <form className="mt-5 space-y-4" onSubmit={onSubmit}>
            <Field
              id="username"
              label={t('login.username')}
              value={username}
              autoComplete="username"
              onChange={setUsername}
            />
            <Field
              id="password"
              label={t('login.password')}
              value={password}
              type="password"
              autoComplete="current-password"
              onChange={setPassword}
            />

            {error && (
              <p role="alert" className="text-sm text-crit">
                {t('login.error')}
              </p>
            )}

            <button
              type="submit"
              disabled={busy}
              className="h-11 w-full rounded-lg bg-signal font-semibold text-signal-ink transition-opacity hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-signal focus-visible:ring-offset-2 focus-visible:ring-offset-bg disabled:opacity-60"
            >
              {busy ? t('login.signingIn') : t('login.submit')}
            </button>
          </form>

          <div className="mt-6 border-t border-line pt-5">
            <p className="text-sm font-medium">{t('login.demoTitle')}</p>
            <p className="mt-1 text-xs text-ink-muted">{t('login.demoHint')}</p>
            <div className="mt-3 flex flex-wrap gap-2">
              {ROLES.map((role) => (
                <button
                  key={role.apiRole}
                  type="button"
                  onClick={() => fillDemo(role.key)}
                  className="rounded-full border border-line px-3 py-1.5 text-xs text-ink-muted transition-colors hover:border-signal hover:text-ink"
                >
                  {t(`roles.${role.key}`)}
                </button>
              ))}
            </div>
            <p className="mt-4 font-mono text-xs text-ink-muted">
              {t('login.demoPassword')}:{' '}
              <span className="text-ink">{DEMO_PASSWORD}</span>
            </p>
          </div>
        </section>
      </main>
    </div>
  )
}

function Field({
  id,
  label,
  value,
  type = 'text',
  autoComplete,
  onChange,
}: {
  id: string
  label: string
  value: string
  type?: string
  autoComplete?: string
  onChange: (value: string) => void
}) {
  return (
    <label htmlFor={id} className="block">
      <span className="mb-1.5 block text-sm text-ink-muted">{label}</span>
      <input
        id={id}
        type={type}
        value={value}
        autoComplete={autoComplete}
        onChange={(event) => onChange(event.target.value)}
        className="h-11 w-full rounded-lg border border-line bg-bg px-3.5 text-ink outline-none transition-colors focus:border-signal focus-visible:ring-2 focus-visible:ring-signal"
      />
    </label>
  )
}

/** A minimal river glyph: a braided channel splitting and rejoining. */
function Mark() {
  return (
    <svg width="22" height="22" viewBox="0 0 22 22" fill="none" aria-hidden>
      <path
        d="M2 6c5 0 5 5 9 5s4-5 9-5M2 16c5 0 5-5 9-5"
        stroke="var(--signal)"
        strokeWidth="1.75"
        strokeLinecap="round"
      />
    </svg>
  )
}

/** Faint braided-channel background evoking the Jamuna. Decorative; hidden from AT. */
function RiverContour() {
  return (
    <svg
      className="pointer-events-none absolute inset-0 h-full w-full opacity-[0.07]"
      viewBox="0 0 1200 800"
      preserveAspectRatio="xMidYMid slice"
      fill="none"
      aria-hidden
    >
      <g stroke="var(--signal)" strokeWidth="1.5" fill="none">
        <path d="M-50 180 C 250 120, 400 320, 650 260 S 1050 160, 1300 300" />
        <path d="M-50 260 C 250 220, 420 400, 650 340 S 1050 260, 1300 380" />
        <path d="M-50 440 C 300 380, 450 560, 700 520 S 1080 460, 1300 560" />
        <path d="M-50 540 C 300 500, 470 660, 720 600 S 1080 560, 1300 640" />
        <path d="M-50 650 C 320 610, 500 740, 760 700 S 1100 660, 1300 720" />
      </g>
    </svg>
  )
}
