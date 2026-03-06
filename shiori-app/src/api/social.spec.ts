import { beforeEach, describe, expect, it, vi } from 'vitest'

import { httpDelete, httpGet, httpPost } from '@/api/http'
import { createPostV2, deletePostV2, listSquareFeedPostsV2, listUserPostsV2 } from '@/api/social'

vi.mock('@/api/http', () => ({
  httpGet: vi.fn(),
  httpPost: vi.fn(),
  httpDelete: vi.fn(),
}))

describe('social api', () => {
  beforeEach(() => {
    vi.mocked(httpGet).mockReset()
    vi.mocked(httpPost).mockReset()
    vi.mocked(httpDelete).mockReset()
  })

  it('should call create post api', async () => {
    vi.mocked(httpPost).mockResolvedValue({} as never)

    await createPostV2({ contentHtml: '<p>hello</p>' })

    expect(httpPost).toHaveBeenCalledTimes(1)
    expect(httpPost).toHaveBeenCalledWith('/api/v2/social/posts', { contentHtml: '<p>hello</p>' })
  })

  it('should call delete post api', async () => {
    vi.mocked(httpDelete).mockResolvedValue({ success: true } as never)

    await deletePostV2(1001)

    expect(httpDelete).toHaveBeenCalledTimes(1)
    expect(httpDelete).toHaveBeenCalledWith('/api/v2/social/posts/1001')
  })

  it('should call author post list api', async () => {
    vi.mocked(httpGet).mockResolvedValue({} as never)

    await listUserPostsV2(2002, { page: 2, size: 20 })

    expect(httpGet).toHaveBeenCalledTimes(1)
    expect(httpGet).toHaveBeenCalledWith('/api/v2/social/users/2002/posts', {
      params: { page: 2, size: 20 },
    })
  })

  it('should call square feed api', async () => {
    vi.mocked(httpGet).mockResolvedValue({} as never)

    await listSquareFeedPostsV2({ page: 1, size: 10 })

    expect(httpGet).toHaveBeenCalledTimes(1)
    expect(httpGet).toHaveBeenCalledWith('/api/v2/social/square/feed', {
      params: {
        page: 1,
        size: 10,
      },
    })
  })
})
