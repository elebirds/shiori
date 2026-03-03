import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

const GATEWAY_BASE_URL = process.env.E2E_GATEWAY_BASE_URL || process.env.VITE_API_BASE_URL || 'http://localhost:8080'
const NOTIFY_HTTP_BASE_URL = process.env.E2E_NOTIFY_HTTP_BASE_URL || 'http://localhost:8090'

test.describe('交易与通知前端烟测', () => {
  test('应完成 注册/登录 -> 创建商品 -> 多SKU下单 -> 支付 -> 收到通知', async ({ page, request }) => {
    await ensureServicesReady(request)

    const runId = `${Date.now()}`
    const seller = {
      username: `e2e_seller_${runId}`,
      password: 'Passw0rd123',
      nickname: `Seller${runId.slice(-4)}`,
    }
    const buyer = {
      username: `e2e_buyer_${runId}`,
      password: 'Passw0rd123',
      nickname: `Buyer${runId.slice(-4)}`,
    }

    await register(page, seller.username, seller.password, seller.nickname)
    await login(page, seller.username, seller.password)

    await page.goto('/sell')
    await expect(page.getByRole('heading', { name: '发布商品' })).toBeVisible()

    const productTitle = `E2E 商品 ${runId}`
    await page.getByLabel('商品标题').fill(productTitle)
    await page.getByLabel('商品描述').fill('Playwright 端到端烟测商品')

    await page.getByPlaceholder('SKU 1').fill('标准版')
    await page.getByRole('button', { name: '添加 SKU' }).click()
    await page.getByPlaceholder('SKU 2').fill('升级版')

    await page.getByRole('button', { name: '创建商品' }).click()
    await page.waitForURL(/\/products\/\d+$/)

    const productIdMatch = page.url().match(/\/products\/(\d+)$/)
    expect(productIdMatch).not.toBeNull()
    const productId = productIdMatch?.[1]
    expect(productId).toBeTruthy()

    await page.getByRole('button', { name: '退出' }).click()
    await page.waitForURL('**/login')

    await register(page, buyer.username, buyer.password, buyer.nickname)
    await login(page, buyer.username, buyer.password)

    await page.goto(`/products/${productId}`)
    await expect(page.getByRole('heading', { name: productTitle })).toBeVisible()

    const quantityInputs = page.locator('input[type="number"][min="0"]')
    await expect(quantityInputs).toHaveCount(2)
    await quantityInputs.nth(0).fill('1')
    await quantityInputs.nth(1).fill('1')

    await page.getByRole('button', { name: '创建订单' }).click()
    await page.waitForURL(/\/orders\/[A-Za-z0-9_-]+$/)

    const orderNoMatch = page.url().match(/\/orders\/([A-Za-z0-9_-]+)$/)
    expect(orderNoMatch).not.toBeNull()
    const orderNo = orderNoMatch?.[1]
    expect(orderNo).toBeTruthy()

    await page.getByRole('button', { name: '立即支付' }).click()
    await expect(page.locator('span').filter({ hasText: 'PAID' }).first()).toBeVisible({ timeout: 20_000 })

    await page.goto('/notifications')
    await expect(page.getByRole('heading', { name: '通知中心' })).toBeVisible()

    const paidEventCard = page
      .locator('article')
      .filter({ hasText: 'OrderPaid' })
      .filter({ hasText: orderNo ?? '' })
      .first()
    await expect(paidEventCard).toBeVisible({ timeout: 20_000 })
  })
})

async function register(page: Page, username: string, password: string, nickname: string): Promise<void> {
  await page.goto('/register')
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('昵称').fill(nickname)
  await page.getByPlaceholder('至少8位').fill(password)
  await page.getByPlaceholder('再次输入密码').fill(password)
  await page.getByRole('button', { name: '注册' }).click()

  try {
    await page.waitForURL('**/login', { timeout: 20_000 })
  } catch {
    const message = (await page.locator('p.text-rose-600').first().textContent())?.trim()
    throw new Error(`注册失败: ${message || '未跳转到登录页'}`)
  }
}

async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('用户名').fill(username)
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  try {
    await page.waitForURL('**/products', { timeout: 20_000 })
  } catch {
    const message = (await page.locator('p.text-rose-600').first().textContent())?.trim()
    throw new Error(`登录失败: ${message || '未跳转到商品页'}`)
  }
}

async function ensureServicesReady(request: APIRequestContext): Promise<void> {
  let gatewayHealth
  try {
    gatewayHealth = await request.get(`${GATEWAY_BASE_URL}/actuator/health`)
  } catch (error) {
    throw new Error(`无法连接网关，请先启动后端服务: ${GATEWAY_BASE_URL}（${String(error)}）`)
  }
  if (!gatewayHealth.ok()) {
    throw new Error(`网关未就绪: ${GATEWAY_BASE_URL}/actuator/health => ${gatewayHealth.status()}`)
  }
  const gatewayData = (await gatewayHealth.json()) as { status?: string }
  if (gatewayData.status !== 'UP') {
    throw new Error(`网关健康状态异常: ${JSON.stringify(gatewayData)}`)
  }

  let notifyHealth
  try {
    notifyHealth = await request.get(`${NOTIFY_HTTP_BASE_URL}/healthz`)
  } catch (error) {
    throw new Error(`无法连接 notify，请先启动通知服务: ${NOTIFY_HTTP_BASE_URL}（${String(error)}）`)
  }
  if (!notifyHealth.ok()) {
    throw new Error(`notify 未就绪: ${NOTIFY_HTTP_BASE_URL}/healthz => ${notifyHealth.status()}`)
  }
  const notifyData = (await notifyHealth.json()) as { status?: string }
  if (notifyData.status !== 'ok') {
    throw new Error(`notify 健康状态异常: ${JSON.stringify(notifyData)}`)
  }
}
