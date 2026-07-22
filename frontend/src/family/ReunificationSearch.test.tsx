import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { ReunificationSearch } from '@/family/ReunificationSearch'
import type { ReunificationResult } from '@/family/api'
import i18n from '@/i18n'

const searchFamilies = vi.fn<(query: string) => Promise<ReunificationResult[]>>()

vi.mock('@/family/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/family/api')>()),
  searchFamilies: (query: string) => searchFamilies(query),
}))

function renderSearch() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <ReunificationSearch />
    </QueryClientProvider>,
  )
}

beforeEach(async () => {
  vi.clearAllMocks()
  await i18n.changeLanguage('en')
})

test('nothing is searched until a query of more than one character is typed', async () => {
  const user = userEvent.setup()
  renderSearch()

  await user.type(screen.getByLabelText('Search by family or group name'), 'R')
  expect(searchFamilies).not.toHaveBeenCalled()
})

test('a match shows only the group name, camp, and status — never a roster', async () => {
  searchFamilies.mockResolvedValue([
    { groupName: 'Rahman Household', campNameEn: 'Kurigram Sadar Govt College Shelter', campNameBn: 'বাংলা', status: 'ARRIVED' },
  ])
  const user = userEvent.setup()
  renderSearch()

  await user.type(screen.getByLabelText('Search by family or group name'), 'Rahman')

  await waitFor(() => expect(searchFamilies).toHaveBeenCalledWith('Rahman'))
  expect(await screen.findByText('Rahman Household')).toBeInTheDocument()
  expect(screen.getByText('Kurigram Sadar Govt College Shelter')).toBeInTheDocument()
  expect(screen.getByText('Arrived')).toBeInTheDocument()
})

test('no matches says so instead of showing nothing at all', async () => {
  searchFamilies.mockResolvedValue([])
  const user = userEvent.setup()
  renderSearch()

  await user.type(screen.getByLabelText('Search by family or group name'), 'Nobody')

  expect(await screen.findByText('No registered group matches that name.')).toBeInTheDocument()
})
