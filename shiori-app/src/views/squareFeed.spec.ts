import { describe, expect, it, vi } from 'vitest'

import { buildSquareAuthorUserIds, FOLLOWING_PAGE_SIZE, loadFollowingUserIds } from '@/views/squareFeed'

describe('square feed logic', () => {
  it('should merge current user and following user ids', () => {
    const ids = buildSquareAuthorUserIds(1001, [1002, 1003, 1002, -1, 0])
    expect(ids).toEqual([1001, 1002, 1003])
  })

  it('should keep only valid following user ids when current user missing', () => {
    const ids = buildSquareAuthorUserIds(null, [0, -1, 2002, 2002])
    expect(ids).toEqual([2002])
  })

  it('should load all following user ids across pages', async () => {
    const fetchFollowingPage = vi
      .fn()
      .mockResolvedValueOnce({
        total: 55,
        page: 1,
        size: FOLLOWING_PAGE_SIZE,
        items: [
          { userId: 2001, userNo: 'U2001', nickname: 'n1', followedAt: '2026-01-01T00:00:00Z' },
          { userId: 2002, userNo: 'U2002', nickname: 'n2', followedAt: '2026-01-01T00:00:00Z' },
        ],
      })
      .mockResolvedValueOnce({
        total: 55,
        page: 2,
        size: FOLLOWING_PAGE_SIZE,
        items: [{ userId: 2003, userNo: 'U2003', nickname: 'n3', followedAt: '2026-01-01T00:00:00Z' }],
      })

    const ids = await loadFollowingUserIds('U1001', fetchFollowingPage)

    expect(fetchFollowingPage).toHaveBeenNthCalledWith(1, 'U1001', { page: 1, size: FOLLOWING_PAGE_SIZE })
    expect(fetchFollowingPage).toHaveBeenNthCalledWith(2, 'U1001', { page: 2, size: FOLLOWING_PAGE_SIZE })
    expect(ids).toEqual([2001, 2002, 2003])
  })

  it('should propagate error when loading following list failed', async () => {
    const fetchFollowingPage = vi.fn().mockRejectedValue(new Error('network down'))
    await expect(loadFollowingUserIds('U1001', fetchFollowingPage)).rejects.toThrow('network down')
  })
})
