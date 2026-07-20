// The seven roles and how the SPA treats each one. `apiRole` is the exact role claim the
// backend puts in the JWT; the lowercase form is also the i18n key (roles.<key>).
//
// Two shell families, per the design: `operator` is the dense situation-room for the people
// running the operation; `field` is the light, large-type, mobile-first surface for people
// in the field and the public.
export type Shell = 'operator' | 'field'

export interface RoleConfig {
  apiRole: string
  key: string
  path: string
  shell: Shell
}

export const ROLES: RoleConfig[] = [
  { apiRole: 'COORDINATOR', key: 'coordinator', path: '/coordinator', shell: 'operator' },
  { apiRole: 'CAMP_MANAGER', key: 'camp_manager', path: '/camp', shell: 'operator' },
  { apiRole: 'ADMIN', key: 'admin', path: '/admin', shell: 'operator' },
  { apiRole: 'DONOR', key: 'donor', path: '/donor', shell: 'field' },
  { apiRole: 'VOLUNTEER', key: 'volunteer', path: '/volunteer', shell: 'field' },
  { apiRole: 'VICTIM', key: 'victim', path: '/victim', shell: 'field' },
  { apiRole: 'NGO', key: 'ngo', path: '/ngo', shell: 'field' },
]

export function configForApiRole(apiRole: string | undefined): RoleConfig | undefined {
  return ROLES.find((r) => r.apiRole === apiRole)
}

export function homePathForRole(apiRole: string | undefined): string {
  return configForApiRole(apiRole)?.path ?? '/login'
}
