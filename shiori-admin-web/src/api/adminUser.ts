import { httpGet, httpPost, httpPut } from '@/api/http'

export interface AdminUserSummary {
  userId: number
  userNo: string
  username: string
  nickname: string
  status: string
  roles: string[]
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
