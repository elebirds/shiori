import { beforeEach, describe, expect, it, vi } from 'vitest'

import { httpDelete, httpGet, httpPost } from '@/api/http'
import { followUser, listUserFollowersByUserNo, listUserFollowingByUserNo, unfollowUser } from '@/api/auth'

vi.mock('@/api/http', () => ({
  httpGet: vi.fn(),
  httpPost: vi.fn(),
  httpPut: vi.fn(),
  httpDelete: vi.fn(),
}))

describe('auth follow api', () => {
  beforeEach(() => {
    vi.mocked(httpGet).mockReset()
    vi.mocked(httpPost).mockReset()
    vi.mocked(httpDelete).mockReset()
  })

  it('should call follow api', async () => {
    vi.mocked(httpPost).mockResolvedValue({ success: true } as never)

    await followUser('U202603060001')

    expect(httpPost).toHaveBeenCalledTimes(1)
    expect(httpPost).toHaveBeenCalledWith('/api/user/follows/U202603060001')
  })

  it('should call unfollow api', async () => {
    vi.mocked(httpDelete).mockResolvedValue({ success: true } as never)

    await unfollowUser('U202603060001')

    expect(httpDelete).toHaveBeenCalledTimes(1)
    expect(httpDelete).toHaveBeenCalledWith('/api/user/follows/U202603060001')
  })

  it('should call followers and following list api', async () => {
    vi.mocked(httpGet).mockResolvedValue({} as never)

    await listUserFollowersByUserNo('U202603060001', { page: 2, size: 20 })
    await listUserFollowingByUserNo('U202603060001', { page: 3, size: 10 })

    expect(httpGet).toHaveBeenNthCalledWith(1, '/api/user/profiles/U202603060001/followers', {
      params: { page: 2, size: 20 },
    })
    expect(httpGet).toHaveBeenNthCalledWith(2, '/api/user/profiles/U202603060001/following', {
      params: { page: 3, size: 10 },
    })
  })
})
