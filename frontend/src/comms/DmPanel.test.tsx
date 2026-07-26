import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DmPanel } from '@/comms/DmPanel'
import { AuthProvider } from '@/auth/AuthContext'
import i18n from '@/i18n'

const useDmContacts = vi.fn()
const useMyUserId = vi.fn()
const useDmThread = vi.fn()

vi.mock('@/comms/useDm', () => ({
  useDmContacts: () => useDmContacts(),
  useMyUserId: () => useMyUserId(),
  useDmThread: (id: number | null) => useDmThread(id),
}))

const contact = { userId: 2, role: 'CAMP_MANAGER', nameEn: 'Anwar Hossain', nameBn: 'আনোয়ার হোসেন' }

function signInAs(role: string) {
  localStorage.setItem('dms.access', 'test-access')
  localStorage.setItem(
    'dms.user',
    JSON.stringify({ username: role.toLowerCase(), role, nameEn: 'Test', nameBn: 'পরীক্ষা' }),
  )
}

function renderPanel() {
  const queryClient = new QueryClient()
  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <DmPanel />
      </AuthProvider>
    </QueryClientProvider>,
  )
}

const send = vi.fn()

beforeEach(async () => {
  localStorage.clear()
  vi.clearAllMocks()
  useDmContacts.mockReturnValue([contact])
  useMyUserId.mockReturnValue(1)
  useDmThread.mockReturnValue({ messages: [], send })
  await i18n.changeLanguage('en')
})

describe('DmPanel', () => {
  it('only offers server-derived contacts, not a free-text picker', () => {
    signInAs('COORDINATOR')
    renderPanel()

    expect(screen.getByText('Anwar Hossain')).toBeInTheDocument()
    expect(screen.queryByPlaceholderText(/username/i)).not.toBeInTheDocument()
  })

  it('sends a message to the selected contact', () => {
    signInAs('COORDINATOR')
    renderPanel()

    fireEvent.click(screen.getByText('Anwar Hossain'))
    fireEvent.change(screen.getByPlaceholderText('Write a message'), { target: { value: 'Status update?' } })
    fireEvent.click(screen.getByText('Send'))

    expect(send).toHaveBeenCalledWith('Status update?')
  })

  it('shows an empty state when the server permits no relationships at all', () => {
    useDmContacts.mockReturnValue([])
    signInAs('DONOR')
    renderPanel()

    expect(screen.getByText('No one to message yet.')).toBeInTheDocument()
  })
})
