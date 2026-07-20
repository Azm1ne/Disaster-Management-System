import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, test } from 'vitest'
import Login from './Login'
import { AuthProvider } from '@/auth/AuthContext'
import i18n from '@/i18n'

function renderLogin() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <Login />
      </AuthProvider>
    </MemoryRouter>,
  )
}

beforeEach(async () => {
  localStorage.clear()
  await i18n.changeLanguage('en')
})

test('login renders in English by default', () => {
  renderLogin()
  expect(screen.getByRole('heading', { name: 'Disaster Management System' })).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Sign in to the operation' })).toBeInTheDocument()
})

test('the language toggle switches every string to বাংলা and persists the choice', async () => {
  renderLogin()
  fireEvent.click(screen.getByRole('button', { name: /Switch language to বাংলা/ }))

  expect(
    await screen.findByRole('heading', { name: 'দুর্যোগ ব্যবস্থাপনা সিস্টেম' }),
  ).toBeInTheDocument()
  // No English title remains — coverage is full, not partial.
  expect(screen.queryByText('Sign in to the operation')).not.toBeInTheDocument()
  expect(localStorage.getItem('dms.lang')).toBe('bn')
})

test('a demo role chip fills the sign-in form', () => {
  renderLogin()
  fireEvent.click(screen.getByRole('button', { name: 'Volunteer' }))
  expect(screen.getByLabelText('Username')).toHaveValue('volunteer')
  expect(screen.getByLabelText('Password')).toHaveValue('relief2026')
})
