import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { AuthProvider } from '@/auth/AuthContext'
import { ProposalInbox } from '@/proposals/ProposalInbox'
import type { Proposal } from '@/proposals/api'
import i18n from '@/i18n'

const disasterCreate: Proposal = {
  id: 1,
  proposalType: 'DISASTER_CREATE',
  targetDisasterId: null,
  payload: { code: 'new-storm', type: 'CYCLONE', nameEn: 'New Storm', nameBn: 'x', geometry: '{}' },
  status: 'PENDING',
  proposedByUserId: 3,
  createdAt: '2026-07-28T00:00:00Z',
  reviewedByUserId: null,
  reviewedAt: null,
  reviewNote: null,
}

const campCreate: Proposal = {
  id: 2,
  proposalType: 'CAMP_CREATE',
  targetDisasterId: 1,
  payload: { code: 'jam-new-camp', nameEn: 'New Camp', nameBn: 'x', lat: 24.2, lng: 89.7, capacity: 500, initialPopulation: 100 },
  status: 'PENDING',
  proposedByUserId: 3,
  createdAt: '2026-07-28T00:00:00Z',
  reviewedByUserId: null,
  reviewedAt: null,
  reviewNote: null,
}

const fetchPendingProposals = vi.fn<() => Promise<Proposal[]>>()
const approveProposal = vi.fn()
const rejectProposal = vi.fn()

vi.mock('@/proposals/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/proposals/api')>()),
  fetchPendingProposals: () => fetchPendingProposals(),
  approveProposal: (...args: unknown[]) => approveProposal(...args),
  rejectProposal: (...args: unknown[]) => rejectProposal(...args),
}))

function signIn() {
  localStorage.setItem('dms.access', 'test-access')
  localStorage.setItem(
    'dms.user',
    JSON.stringify({ username: 'central_authority', role: 'CENTRAL_AUTHORITY', nameEn: 'Reviewer', nameBn: 'x' }),
  )
}

function renderInbox() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider>
        <ProposalInbox />
      </AuthProvider>
    </QueryClientProvider>,
  )
}

beforeEach(async () => {
  localStorage.clear()
  vi.clearAllMocks()
  signIn()
  await i18n.changeLanguage('en')
})

test('renders pending proposals with a readable one-line summary per type', async () => {
  fetchPendingProposals.mockResolvedValue([disasterCreate, campCreate])
  renderInbox()

  expect(await screen.findByText('Create disaster: new-storm')).toBeInTheDocument()
  expect(screen.getByText('Create camp: jam-new-camp (pop 100)')).toBeInTheDocument()
  expect(screen.getByText('New disaster')).toBeInTheDocument()
  expect(screen.getByText('New camp')).toBeInTheDocument()
})

test('shows the empty state once there is nothing pending', async () => {
  fetchPendingProposals.mockResolvedValue([])
  renderInbox()

  expect(await screen.findByText('Nothing pending. New proposals will appear here.')).toBeInTheDocument()
})

test('approve is a single click that calls the approve endpoint and refetches the queue', async () => {
  fetchPendingProposals.mockResolvedValueOnce([disasterCreate]).mockResolvedValueOnce([])
  approveProposal.mockResolvedValue({ ...disasterCreate, status: 'APPROVED' })
  const user = userEvent.setup()
  renderInbox()

  await screen.findByText('Create disaster: new-storm')
  await user.click(screen.getByText('Approve'))

  await waitFor(() => expect(approveProposal).toHaveBeenCalledWith(expect.anything(), 1, undefined))
  expect(fetchPendingProposals).toHaveBeenCalledTimes(2)
  expect(await screen.findByText('Nothing pending. New proposals will appear here.')).toBeInTheDocument()
})

test('reject requires a second confirming click, then calls the reject endpoint and refetches', async () => {
  fetchPendingProposals.mockResolvedValueOnce([disasterCreate]).mockResolvedValueOnce([])
  rejectProposal.mockResolvedValue({ ...disasterCreate, status: 'REJECTED' })
  const user = userEvent.setup()
  renderInbox()

  await screen.findByText('Create disaster: new-storm')
  await user.click(screen.getByText('Reject'))
  expect(rejectProposal).not.toHaveBeenCalled()

  await user.click(screen.getByText('Confirm reject'))

  await waitFor(() => expect(rejectProposal).toHaveBeenCalledWith(expect.anything(), 1, undefined))
  expect(fetchPendingProposals).toHaveBeenCalledTimes(2)
  expect(await screen.findByText('Nothing pending. New proposals will appear here.')).toBeInTheDocument()
})
