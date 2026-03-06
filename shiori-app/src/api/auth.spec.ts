import { beforeEach, describe, expect, it, vi } from 'vitest'

import { httpDelete, httpGet, httpPost, httpPut } from '@/api/http'
import {
  createMyAddress,
  deleteMyAddress,
  followUser,
  listMyAddresses,
  listUserFollowersByUserNo,
  listUserFollowingByUserNo,
  setMyAddressDefault,
  unfollowUser,
  updateMyAddress,
} from '@/api/auth'

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
    vi.mocked(httpPut).mockReset()
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

  it('should call my address apis', async () => {
    vi.mocked(httpGet).mockResolvedValue([] as never)
    vi.mocked(httpPost).mockResolvedValue({} as never)
    vi.mocked(httpPut).mockResolvedValue({} as never)
    vi.mocked(httpDelete).mockResolvedValue(undefined as never)

    await listMyAddresses()
    await createMyAddress({
      receiverName: '张三',
      receiverPhone: '13800138000',
      province: '广东省',
      city: '深圳市',
      district: '南山区',
      detailAddress: '科技园 1 号',
      isDefault: true,
    })
    await updateMyAddress(101, {
      receiverName: '李四',
      receiverPhone: '13900139000',
      province: '北京市',
      city: '北京市',
      district: '海淀区',
      detailAddress: '中关村 2 号',
      isDefault: false,
    })
    await setMyAddressDefault(101)
    await deleteMyAddress(101)

    expect(httpGet).toHaveBeenCalledWith('/api/user/me/addresses')
    expect(httpPost).toHaveBeenNthCalledWith(1, '/api/user/me/addresses', {
      receiverName: '张三',
      receiverPhone: '13800138000',
      province: '广东省',
      city: '深圳市',
      district: '南山区',
      detailAddress: '科技园 1 号',
      isDefault: true,
    })
    expect(httpPut).toHaveBeenCalledWith('/api/user/me/addresses/101', {
      receiverName: '李四',
      receiverPhone: '13900139000',
      province: '北京市',
      city: '北京市',
      district: '海淀区',
      detailAddress: '中关村 2 号',
      isDefault: false,
    })
    expect(httpPost).toHaveBeenNthCalledWith(2, '/api/user/me/addresses/101/default')
    expect(httpDelete).toHaveBeenCalledWith('/api/user/me/addresses/101')
  })
})
