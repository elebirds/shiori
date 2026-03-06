import { listUserFollowingByUserNo, type FollowUserPageResponse } from '@/api/auth'

export const FOLLOWING_PAGE_SIZE = 50
export const FOLLOWING_FETCH_GUARD = 200

type ListFollowingPageFn = (
  userNo: string,
  params: { page?: number; size?: number },
) => Promise<FollowUserPageResponse>

export function buildSquareAuthorUserIds(currentUserId: number | null, followingUserIds: number[]): number[] {
  const ids = new Set<number>()
  if (currentUserId != null && currentUserId > 0) {
    ids.add(currentUserId)
  }
  for (const userId of followingUserIds) {
    if (Number.isFinite(userId) && userId > 0) {
      ids.add(userId)
    }
  }
  return Array.from(ids)
}

export async function loadFollowingUserIds(
  userNo: string,
  listFollowingPage: ListFollowingPageFn = listUserFollowingByUserNo,
): Promise<number[]> {
  const ids = new Set<number>()
  let page = 1

  for (let guard = 0; guard < FOLLOWING_FETCH_GUARD; guard += 1) {
    const response = await listFollowingPage(userNo, { page, size: FOLLOWING_PAGE_SIZE })
    const items = response.items || []
    for (const item of items) {
      if (item.userId > 0) {
        ids.add(item.userId)
      }
    }
    const totalPages = Math.max(1, Math.ceil((response.total || 0) / FOLLOWING_PAGE_SIZE))
    if (page >= totalPages || items.length === 0) {
      break
    }
    page += 1
  }

  return Array.from(ids)
}
