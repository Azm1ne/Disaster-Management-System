// The demo simulation control surface. Reading the clock is open to any signed-in user; the
// mutating calls are restricted to Coordinator/Admin server-side, so a refusal here is a 403,
// not a hidden button.

export type SimulationPhase = 'SURGE' | 'NEW_CAMP' | 'RELIEF_CONVOY' | 'RECOVERY'

export interface SimulationClock {
  tick: number
  /** ISO-8601 instant of the simulated moment (30 min of sim-time per tick). */
  simTime: string
  phase: SimulationPhase
  running: boolean
  speed: number
  scenarioLength: number
}

/** The playback speeds the engine accepts; anything else is rejected with a 400. */
export const SPEEDS = [0.5, 1, 2, 4, 8] as const

type Fetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>

async function control(authFetch: Fetcher, path: string, body?: unknown): Promise<SimulationClock> {
  const response = await authFetch(`/api/simulation/${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) throw new Error(`simulation_${path}_failed_${response.status}`)
  return (await response.json()) as SimulationClock
}

export async function fetchClock(authFetch: Fetcher): Promise<SimulationClock> {
  const response = await authFetch('/api/simulation/clock')
  if (!response.ok) throw new Error(`simulation_clock_failed_${response.status}`)
  return (await response.json()) as SimulationClock
}

export const pauseSimulation = (authFetch: Fetcher) => control(authFetch, 'pause')
export const resumeSimulation = (authFetch: Fetcher) => control(authFetch, 'resume')
export const resetSimulation = (authFetch: Fetcher) => control(authFetch, 'reset')
export const setSimulationSpeed = (authFetch: Fetcher, multiplier: number) =>
  control(authFetch, 'speed', { multiplier })
