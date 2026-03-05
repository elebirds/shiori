import { execFileSync } from 'node:child_process'
import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

const GATEWAY_BASE_URL = process.env.E2E_GATEWAY_BASE_URL || 'http://127.0.0.1:8080'
const MYSQL_CONTAINER = process.env.E2E_MYSQL_CONTAINER || 'shiori-mysql'
const MYSQL_USER = mustEnv('E2E_MYSQL_USER')
const MYSQL_PASSWORD = mustEnv('E2E_MYSQL_PASSWORD')

function mustEnv(name: string): string {
  const value = process.env[name]
  if (!value) {
    throw new Error(`missing env: ${name}`)
  }
  return value
}

interface TokenPairResponse {
  accessToken: string
  user: {
    userId: number
  }
}

interface PreparedData {
  admin: {
    username: string
    password: string
    userId: number
  }
  seller: {
    username: string
    password: string
    userId: number
    token: string
  }
  buyer: {
    username: string
    password: string
    userId: number
    token: string
  }
  productTitle: string
  productId: number
  orderNo: string
}

test.describe('管理端前端烟测', () => {
  test('应完成 登录 -> 用户禁用/启用 -> 商品下架 -> 订单取消', async ({ page, request }) => {
    await ensureGatewayReady(request)
    const prepared = await prepareData(request)

    await login(page, prepared.admin.username, prepared.admin.password)

    await page.goto('/payments/cdks')
    await expect(page.getByRole('heading', { name: '支付 / CDK 管理' })).toBeVisible()
    await page.getByRole('spinbutton').first().fill('3')
    await page.getByRole('spinbutton').nth(1).fill('1200')
    await page.getByRole('button', { name: '创建批次' }).click()
    await expect(page.getByText('创建成功：批次')).toBeVisible({ timeout: 20_000 })
    const downloadPromise = page.waitForEvent('download')
    await page.getByRole('button', { name: '下载 CSV' }).click()
    const download = await downloadPromise
    expect(download.suggestedFilename()).toContain('cdk-batch-')

    await page.goto('/users')
    await page.getByPlaceholder('用户名/昵称/userNo').fill(prepared.buyer.username)
    await page.getByRole('button', { name: '查询' }).click()
    await expect(page.locator('tr').filter({ hasText: prepared.buyer.username }).first()).toBeVisible()

    await page.locator('tr').filter({ hasText: prepared.buyer.username }).first().getByRole('button', { name: '禁用' }).click()
    await expect
      .poll(() => fetchAdminUserStatus(request, prepared.admin.username, prepared.admin.password, prepared.buyer.userId))
      .toBe('DISABLED')

    await page.locator('tr').filter({ hasText: prepared.buyer.username }).first().getByRole('button', { name: '启用' }).click()
    await expect
      .poll(() => fetchAdminUserStatus(request, prepared.admin.username, prepared.admin.password, prepared.buyer.userId))
      .toBe('ENABLED')

    await page.getByRole('link', { name: '商品管理' }).click()
    await expect(page.getByRole('heading', { name: '商品管理' })).toBeVisible()
    await page.getByPlaceholder('标题/描述/商品编号').fill(prepared.productTitle)
    await page.getByRole('button', { name: '查询' }).click()
    await expect(page.locator('tr').filter({ hasText: prepared.productTitle }).first()).toBeVisible()
    await page.locator('tr').filter({ hasText: prepared.productTitle }).first().getByRole('button', { name: prepared.productTitle }).click()
    await expect(page.getByText('后台富文本详情')).toBeVisible()
    await page.locator('tr').filter({ hasText: prepared.productTitle }).first().getByRole('button', { name: '强制下架' }).click()
    await expect
      .poll(() => fetchAdminProductStatus(request, prepared.admin.username, prepared.admin.password, prepared.productId))
      .toBe('OFF_SHELF')

    await page.getByRole('link', { name: '订单管理' }).click()
    await expect(page.getByRole('heading', { name: '订单管理' })).toBeVisible()
    await page.getByPlaceholder('orderNo').fill(prepared.orderNo)
    await page.getByRole('button', { name: '查询' }).click()
    await expect(page.locator('tr').filter({ hasText: prepared.orderNo }).first()).toBeVisible()
    await page.locator('tr').filter({ hasText: prepared.orderNo }).first().getByRole('button', { name: '取消' }).click()
    await expect
      .poll(() => fetchAdminOrderStatus(request, prepared.admin.username, prepared.admin.password, prepared.orderNo))
      .toBe('CANCELED')
  })
})

async function ensureGatewayReady(request: APIRequestContext): Promise<void> {
  const response = await request.get(`${GATEWAY_BASE_URL}/actuator/health`)
  if (!response.ok()) {
    throw new Error(`网关未就绪: ${response.status()}`)
  }
  const data = (await response.json()) as { status?: string }
  if (data.status !== 'UP') {
    throw new Error(`网关健康状态异常: ${JSON.stringify(data)}`)
  }
}

async function prepareData(request: APIRequestContext): Promise<PreparedData> {
  const runId = `${Date.now()}`
  const suffix = runId.slice(-8)
  const password = 'Passw0rd123'
  const adminUsername = `adminui${suffix}`
  const sellerUsername = `sellerui${suffix}`
  const buyerUsername = `buyerui${suffix}`

  const adminRegister = await register(request, adminUsername, password, `Admin${suffix}`)
  const sellerRegister = await register(request, sellerUsername, password, `Seller${suffix}`)
  const buyerRegister = await register(request, buyerUsername, password, `Buyer${suffix}`)

  grantAdminRole(adminUsername)

  const adminLogin = await loginByApi(request, adminUsername, password)
  const sellerLogin = await loginByApi(request, sellerUsername, password)
  const buyerLogin = await loginByApi(request, buyerUsername, password)

  const productTitle = `后台E2E商品${suffix}`
  const productCreate = await callApi<{ productId: number }>(
    request,
    'POST',
    '/api/v2/product/products',
    sellerLogin.accessToken,
    {
      title: productTitle,
      description: 'admin-web e2e',
      detailHtml: '<p><span style="font-size:18px;">后台富文本详情</span></p>',
      coverObjectKey: null,
      categoryCode: 'TEXTBOOK',
      conditionLevel: 'GOOD',
      tradeMode: 'MEETUP',
      campusCode: 'main_campus',
      skus: [{ skuName: '默认款', specJson: '{"edition":"std"}', priceCent: 1888, stock: 30 }],
    },
  )
  const productId = productCreate.productId

  await callApi(
    request,
    'POST',
    `/api/v2/product/products/${productId}/publish`,
    sellerLogin.accessToken,
    undefined,
  )

  const detail = await callApi<{ skus: Array<{ skuId: number }> }>(
    request,
    'GET',
    `/api/v2/product/products/${productId}`,
    '',
    undefined,
  )
  const skuId = detail.skus[0]?.skuId
  if (!skuId) {
    throw new Error('无法获取商品 SKU')
  }

  const orderCreate = await callApi<{ orderNo: string }>(
    request,
    'POST',
    '/api/v2/order/orders',
    buyerLogin.accessToken,
    {
      items: [{ productId, skuId, quantity: 1 }],
    },
    {
      'Idempotency-Key': `admin-ui-${suffix}`,
    },
  )

  return {
    admin: {
      username: adminUsername,
      password,
      userId: adminRegister.userId,
    },
    seller: {
      username: sellerUsername,
      password,
      userId: sellerRegister.userId,
      token: sellerLogin.accessToken,
    },
    buyer: {
      username: buyerUsername,
      password,
      userId: buyerRegister.userId,
      token: buyerLogin.accessToken,
    },
    productTitle,
    productId,
    orderNo: orderCreate.orderNo,
  }
}

async function register(
  request: APIRequestContext,
  username: string,
  password: string,
  nickname: string,
): Promise<{ userId: number }> {
  return callApi<{ userId: number }>(
    request,
    'POST',
    '/api/user/auth/register',
    '',
    { username, password, nickname },
  )
}

function grantAdminRole(username: string): void {
  const sql = [
    'INSERT INTO u_user_role (user_id, role_id, created_at)',
    'SELECT u.id, r.id, CURRENT_TIMESTAMP(3)',
    'FROM u_user u',
    "JOIN u_role r ON r.role_code = 'ROLE_ADMIN' AND r.status = 1 AND r.is_deleted = 0",
    `WHERE u.username = '${username}'`,
    '  AND u.is_deleted = 0',
    'ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);',
  ].join(' ')

  execFileSync(
    'docker',
    [
      'exec',
      MYSQL_CONTAINER,
      'mysql',
      `-u${MYSQL_USER}`,
      `-p${MYSQL_PASSWORD}`,
      'shiori_user',
      '-e',
      sql,
    ],
    { stdio: 'pipe' },
  )
}

async function loginByApi(request: APIRequestContext, username: string, password: string): Promise<TokenPairResponse> {
  return callApi<TokenPairResponse>(request, 'POST', '/api/user/auth/login', '', { username, password })
}

async function fetchAdminUserStatus(
  request: APIRequestContext,
  adminUsername: string,
  adminPassword: string,
  userId: number,
): Promise<string> {
  const admin = await loginByApi(request, adminUsername, adminPassword)
  const detail = await callApi<{ status: string }>(
    request,
    'GET',
    `/api/admin/users/${userId}`,
    admin.accessToken,
    undefined,
  )
  return detail.status
}

async function fetchAdminProductStatus(
  request: APIRequestContext,
  adminUsername: string,
  adminPassword: string,
  productId: number,
): Promise<string> {
  const admin = await loginByApi(request, adminUsername, adminPassword)
  const detail = await callApi<{ status: string }>(
    request,
    'GET',
    `/api/v2/admin/products/${productId}`,
    admin.accessToken,
    undefined,
  )
  return detail.status
}

async function fetchAdminOrderStatus(
  request: APIRequestContext,
  adminUsername: string,
  adminPassword: string,
  orderNo: string,
): Promise<string> {
  const admin = await loginByApi(request, adminUsername, adminPassword)
  const detail = await callApi<{ status: string }>(
    request,
    'GET',
    `/api/v2/admin/orders/${orderNo}`,
    admin.accessToken,
    undefined,
  )
  return detail.status
}

async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL('**/users')
}

async function callApi<T>(
  request: APIRequestContext,
  method: 'GET' | 'POST' | 'PUT',
  path: string,
  token: string,
  body: unknown,
  headers: Record<string, string> = {},
): Promise<T> {
  const requestHeaders: Record<string, string> = { ...headers }
  if (token) {
    requestHeaders.Authorization = `Bearer ${token}`
  }

  let response
  if (method === 'GET') {
    response = await request.get(`${GATEWAY_BASE_URL}${path}`, { headers: requestHeaders })
  } else if (method === 'POST') {
    response = await request.post(`${GATEWAY_BASE_URL}${path}`, {
      headers: requestHeaders,
      data: body,
    })
  } else {
    response = await request.put(`${GATEWAY_BASE_URL}${path}`, {
      headers: requestHeaders,
      data: body,
    })
  }

  if (!response.ok()) {
    throw new Error(`API 请求失败: ${method} ${path} => ${response.status()} ${await response.text()}`)
  }

  const payload = (await response.json()) as {
    code: number
    message: string
    data: T
  }
  if (payload.code !== 0) {
    throw new Error(`API 业务失败: ${method} ${path} => code=${payload.code}, message=${payload.message}`)
  }
  return payload.data
}
