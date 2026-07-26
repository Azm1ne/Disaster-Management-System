import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { BroadcastPanel } from '@/comms/BroadcastPanel'
import { AuthProvider } from '@/auth/AuthContext'
import i18n from '@/i18n'

const useBroadcasts = vi.fn()
const useBroadcastReceipts = vi.fn()

vi.mock('@/comms/useBroadcasts', () => ({ useBroadcasts: () => useBroadcasts() }))
vi.mock('@/comms/useBroadcastReceipts', () => ({ useBroadcastReceipts: () => useBroadcastReceipts() }))

const broadcast = {
  id: 1,
  senderUserId: 1,
  targetRole: 'CAMP_MANAGER' as const,
  bodyEn: 'Convoy delayed 2 hours',
  bodyBn: 'কনভয় ২ ঘণ্টা বিলম্বিত',
  createdAt: '2026-07-24T00:00:00Z',
}

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
        <BroadcastPanel />
      </AuthProvider>
    </QueryClientProvider>,
  )
}

const send = vi.fn().mockResolvedValue(undefined)
const markRead = vi.fn().mockResolvedValue(undefined)

beforeEach(async () => {
  localStorage.clear()
  vi.clearAllMocks()
  useBroadcasts.mockReturnValue({ broadcasts: [broadcast], send, markRead })
  useBroadcastReceipts.mockReturnValue([])
  await i18n.changeLanguage('en')
})

describe('BroadcastPanel', () => {
  it('lets a Coordinator compose and send a bilingual broadcast', () => {
    signInAs('COORDINATOR')
    renderPanel()

    expect(screen.getByText('Broadcast')).toBeInTheDocument()
    fireEvent.change(screen.getByPlaceholderText('Message (English)'), {
      target: { value: 'Water levels rising' },
    })
    fireEvent.change(screen.getByPlaceholderText('বার্তা (বাংলা)'), {
      target: { value: 'পানির স্তর বাড়ছে' },
    })
    fireEvent.click(screen.getByText('Broadcast'))

    expect(send).toHaveBeenCalledWith({
      targetRole: 'CAMP_MANAGER',
      bodyEn: 'Water levels rising',
      bodyBn: 'পানির স্তর বাড়ছে',
    })
  })

  it('shows a Camp Manager the English body and marks it read on open, without a compose form', () => {
    signInAs('CAMP_MANAGER')
    renderPanel()

    expect(screen.queryByText('Broadcast')).not.toBeInTheDocument()
    fireEvent.click(screen.getByText('Convoy delayed 2 hours'))

    expect(markRead).toHaveBeenCalledWith(1)
  })

  it('renders the Bengali body when the language is switched', async () => {
    await i18n.changeLanguage('bn')
    signInAs('CAMP_MANAGER')
    renderPanel()

    expect(screen.getByText('কনভয় ২ ঘণ্টা বিলম্বিত')).toBeInTheDocument()
  })
})
