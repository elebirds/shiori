import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

const GATEWAY_BASE_URL = process.env.E2E_GATEWAY_BASE_URL || process.env.VITE_API_BASE_URL || 'http://localhost:8080'
const NOTIFY_HTTP_BASE_URL = process.env.E2E_NOTIFY_HTTP_BASE_URL || 'http://localhost:8090'

test.describe('聊天前端烟测', () => {
  test('应完成 商品详情咨询 -> 会话中心发送消息', async ({ page, request }) => {
    await ensureServicesReady(request)

    const runId = `${Date.now()}`
    const seller = {
      username: `e2e_chat_seller_${runId}`,
      password: 'Passw0rd123',
      nickname: `Seller${runId.slice(-4)}`,
    }
    const buyer = {
      username: `e2e_chat_buyer_${runId}`,
      password: 'Passw0rd123',
      nickname: `Buyer${runId.slice(-4)}`,
    }

    await register(page, seller.username, seller.password, seller.nickname)
    await login(page, seller.username, seller.password)

    await page.goto('/sell')
    const productTitle = `Chat E2E 商品 ${runId}`
    await page.getByLabel('商品标题').fill(productTitle)
    await page.getByLabel('商品简介').fill('chat e2e')
    await page.getByPlaceholder('SKU 1').fill('标准版')
    await page.getByRole('button', { name: '创建商品' }).click()
    await page.waitForURL(/\/products\/\d+$/)
    const productId = Number((page.url().match(/\/products\/(\d+)$/) || [])[1])
    expect(productId).toBeGreaterThan(0)

    await page.getByRole('button', { name: '退出' }).click()
    await page.waitForURL('**/login')

    await register(page, buyer.username, buyer.password, buyer.nickname)
    await login(page, buyer.username, buyer.password)

    await page.goto(`/products/${productId}`)
    await page.getByRole('button', { name: '咨询卖家' }).click()
    await page.waitForURL(/\/chat\?conversationId=\d+$/, { timeout: 20_000 })
    await expect(page.getByRole('heading', { name: '聊天中心' })).toBeVisible()

    await page.getByPlaceholder('输入咨询内容（纯文本）').fill('你好，我想咨询库存')
    await page.getByRole('button', { name: '发送' }).click()
    await expect(page.getByText('你好，我想咨询库存')).toBeVisible({ timeout: 10_000 })
  })
})

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

async function ensureServicesReady(request: APIRequestContext): Promise<void> {
  const gatewayHealth = await request.get(`${GATEWAY_BASE_URL}/actuator/health`)
  if (!gatewayHealth.ok()) {
    throw new Error(`网关未就绪: ${gatewayHealth.status()}`)
  }
  const notifyHealth = await request.get(`${NOTIFY_HTTP_BASE_URL}/healthz`)
  if (!notifyHealth.ok()) {
    throw new Error(`notify 未就绪: ${notifyHealth.status()}`)
  }
}
