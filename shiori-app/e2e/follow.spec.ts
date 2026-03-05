import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

const GATEWAY_BASE_URL = process.env.E2E_GATEWAY_BASE_URL || process.env.VITE_API_BASE_URL || 'http://localhost:8080'

test.describe('关注关系前端烟测', () => {
  test('应完成 关注 -> 粉丝列表可见 -> 取关 回退', async ({ page, request }) => {
    await ensureGatewayReady(request)

    const runId = `${Date.now()}`
    const userA = {
      username: `e2e_follow_a_${runId}`,
      password: 'Passw0rd123',
      nickname: `FollowA${runId.slice(-4)}`,
    }
    const userB = {
      username: `e2e_follow_b_${runId}`,
      password: 'Passw0rd123',
      nickname: `FollowB${runId.slice(-4)}`,
    }

    await register(page, userB.username, userB.password, userB.nickname)
    await login(page, userB.username, userB.password)
    const userNoB = await getCurrentUserNo(page)
    await logout(page)

    await register(page, userA.username, userA.password, userA.nickname)
    await login(page, userA.username, userA.password)
    const userNoA = await getCurrentUserNo(page)

    await page.goto(`/u/${userNoB}`)
    const followButton = page.getByRole('button', { name: '关注' })
    await expect(followButton).toBeVisible()
    await followButton.click()
    await expect(page.getByRole('button', { name: '已关注' })).toBeVisible({ timeout: 10_000 })

    await page.getByRole('button', { name: /粉丝/ }).click()
    await expect(page).toHaveURL(new RegExp(`/u/${userNoB}/followers`))
    await expect(page.getByText(`@${userNoA}`)).toBeVisible({ timeout: 10_000 })

    await page.goto(`/u/${userNoB}`)
    await page.getByRole('button', { name: '已关注' }).click()
    await expect(page.getByRole('button', { name: '关注' })).toBeVisible({ timeout: 10_000 })

    await page.getByRole('button', { name: /粉丝/ }).click()
    await expect(page).toHaveURL(new RegExp(`/u/${userNoB}/followers`))
    await expect(page.getByText('还没有粉丝')).toBeVisible({ timeout: 10_000 })
  })
})

async function ensureGatewayReady(request: APIRequestContext): Promise<void> {
  const gatewayHealth = await request.get(`${GATEWAY_BASE_URL}/actuator/health`)
  if (!gatewayHealth.ok()) {
    throw new Error(`网关未就绪: ${gatewayHealth.status()}`)
  }
}

async function register(page: Page, username: string, password: string, nickname: string): Promise<void> {
  await page.goto('/register')
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('昵称').fill(nickname)
  await page.getByPlaceholder('至少8位').fill(password)
  await page.getByPlaceholder('再次输入密码').fill(password)
  await page.getByRole('button', { name: '注册' }).click()
  await page.waitForURL('**/login', { timeout: 20_000 })
}

async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('用户名').fill(username)
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL('**/products', { timeout: 20_000 })
}

async function logout(page: Page): Promise<void> {
  await page.getByRole('button', { name: '退出' }).click()
  await page.waitForURL('**/login', { timeout: 20_000 })
}

async function getCurrentUserNo(page: Page): Promise<string> {
  const userNo = await page.evaluate(() => {
    const raw = localStorage.getItem('shiori_auth_user')
    if (!raw) {
      return ''
    }
    try {
      const parsed = JSON.parse(raw) as { userNo?: string }
      return (parsed.userNo || '').trim()
    } catch {
      return ''
    }
  })
  if (!userNo) {
    throw new Error('未获取到当前 userNo')
  }
  return userNo
}
