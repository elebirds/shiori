import { httpGet, httpPost, httpPut } from '@/api/http'

export interface AdminUserSummary {
  userId: number
  userNo: string
  username: string
  nickname: string
  status: string
  roles: string[]
  mustChangePassword?: boolean
  lastLoginAt?: string
  createdAt: string
}

export interface AdminUserDetail {
  userId: number
  userNo: string
  username: string
  nickname: string
  avatarUrl?: string
  status: string
  failedLoginCount?: number
  lockedUntil?: string
  mustChangePassword?: boolean
  lastLoginAt?: string
  lastLoginIp?: string
  roles: string[]
  createdAt: string
  updatedAt: string
}

export interface AdminUserPageResponse {
  total: number
  page: number
  size: number
  items: AdminUserSummary[]
}

export interface AdminRoleResponse {
  roleCode: string
  roleName: string
}

export interface AdminUserAuditItem {
  id: number
  operatorUserId: number
  targetUserId: number
  action: string
  beforeJson?: string
  afterJson?: string
  reason?: string
  createdAt: string
}

export interface AdminUserAuditPageResponse {
  total: number
  page: number
  size: number
  items: AdminUserAuditItem[]
}

export interface UpdateStatusPayload {
  status: 'ENABLED' | 'DISABLED'
  reason?: string
}

export interface UpdateAdminRolePayload {
  grantAdmin: boolean
  reason?: string
}

export interface AdminUserStatusResponse {
  userId: number
  status: string
  admin: boolean
}

export interface AdminUserLockPayload {
  durationMinutes?: number
  reason?: string
}

export interface AdminUserUnlockPayload {
  reason?: string
}

export interface AdminUserPasswordResetPayload {
  newPassword: string
  forceChangePassword?: boolean
  reason?: string
}

export function listAdminUsers(params: {
  page?: number
  size?: number
  keyword?: string
  status?: string
  role?: string
}): Promise<AdminUserPageResponse> {
  return httpGet('/api/admin/users', { params })
}

export function getAdminUser(userId: number): Promise<AdminUserDetail> {
  return httpGet(`/api/admin/users/${userId}`)
}

export function updateAdminUserStatus(userId: number, payload: UpdateStatusPayload): Promise<AdminUserStatusResponse> {
  return httpPost(`/api/admin/users/${userId}/status`, payload)
}

export function updateAdminRole(userId: number, payload: UpdateAdminRolePayload): Promise<AdminUserStatusResponse> {
  return httpPut(`/api/admin/users/${userId}/admin-role`, payload)
}

export function listAdminRoles(): Promise<AdminRoleResponse[]> {
  return httpGet('/api/admin/roles')
}

export function listAdminUserAudits(
  userId: number,
  params?: {
    page?: number
    size?: number
    action?: string
  },
): Promise<AdminUserAuditPageResponse> {
  return httpGet(`/api/admin/users/${userId}/audits`, { params })
}

export function lockAdminUser(userId: number, payload?: AdminUserLockPayload): Promise<AdminUserStatusResponse> {
  return httpPost(`/api/admin/users/${userId}/lock`, payload || {})
}

export function unlockAdminUser(userId: number, payload?: AdminUserUnlockPayload): Promise<AdminUserStatusResponse> {
  return httpPost(`/api/admin/users/${userId}/unlock`, payload || {})
}

export function resetAdminUserPassword(
  userId: number,
  payload: AdminUserPasswordResetPayload,
): Promise<AdminUserStatusResponse> {
  return httpPost(`/api/admin/users/${userId}/password/reset`, payload)
}
