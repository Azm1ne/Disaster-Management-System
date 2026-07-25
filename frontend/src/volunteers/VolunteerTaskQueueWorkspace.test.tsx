import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, expect, test, vi } from 'vitest'
import { VolunteerTaskQueueWorkspace } from '@/volunteers/VolunteerTaskQueueWorkspace'
import type { SkillCoverage, VolunteerCandidate, VolunteerTaskSummary } from '@/volunteers/api'
import i18n from '@/i18n'

const task: VolunteerTaskSummary = {
  id: 1,
  alertId: 9,
  campId: 2,
  requiredSkill: 'MEDICAL',
  description: 'Medical support volunteers needed',
  status: 'OPEN',
  assignedVolunteerId: null,
  assignedVolunteerNameEn: null,
  assignedVolunteerNameBn: null,
  assignmentMethod: null,
  urgencyScore: 0.8,
  generatedAtTick: 5,
  assignedAtTick: null,
  canAssign: true,
  canAccept: false,
  createdAt: '2026-07-24T00:00:00Z',
  updatedAt: '2026-07-24T00:00:00Z',
}

const candidate: VolunteerCandidate = {
  volunteerId: 1,
  nameEn: 'Sabbir Rahman',
  nameBn: 'সাব্বির রহমান',
  hasSkill: true,
  distanceKm: 1.2,
  skillScore: 1,
  distanceScore: 0.9,
  urgencyScore: 0.8,
  score: 0.72,
}

const coverage: SkillCoverage = {
  skill: 'SECURITY',
  openTaskCount: 1,
  availableVolunteerCount: 0,
  gap: 1,
  unmet: true,
}

const assign = vi.fn()
const tasksMock = vi.fn<() => VolunteerTaskSummary[] | undefined>(() => [task])
const candidatesMock = vi.fn<() => VolunteerCandidate[] | undefined>(() => [candidate])
const skillGapMock = vi.fn<() => SkillCoverage[] | undefined>(() => [coverage])

vi.mock('@/volunteers/useVolunteers', () => ({
  useVolunteerTasks: () => tasksMock(),
  useSkillGap: () => skillGapMock(),
  useCandidates: () => candidatesMock(),
  useAssignVolunteer: () => assign,
}))

beforeEach(async () => {
  vi.clearAllMocks()
  tasksMock.mockReturnValue([task])
  candidatesMock.mockReturnValue([candidate])
  skillGapMock.mockReturnValue([coverage])
  await i18n.changeLanguage('en')
})

test('shows the skill-coverage gap for an unmet skill', () => {
  render(<VolunteerTaskQueueWorkspace />)
  expect(screen.getByText('Security')).toBeInTheDocument()
  expect(screen.getByText('0/1')).toBeInTheDocument()
})

test('expanding a task shows its ranked candidates with a one-click assign', () => {
  render(<VolunteerTaskQueueWorkspace />)

  fireEvent.click(screen.getByText('Show candidates'))
  expect(screen.getByText('Sabbir Rahman')).toBeInTheDocument()

  fireEvent.click(screen.getByText('Assign'))
  expect(assign).toHaveBeenCalledWith(1, 1)
})

test('a task with no assign entitlement never shows the candidates toggle', () => {
  tasksMock.mockReturnValue([{ ...task, canAssign: false }])
  render(<VolunteerTaskQueueWorkspace />)
  expect(screen.queryByText('Show candidates')).not.toBeInTheDocument()
})

test('nothing is shown before tasks have loaded', () => {
  tasksMock.mockReturnValue(undefined)
  const { container } = render(<VolunteerTaskQueueWorkspace />)
  expect(container).toBeEmptyDOMElement()
})
