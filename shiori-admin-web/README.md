# shiori-admin-web

独立管理端（Vue3 + TS + Vite + Tailwind），用于平台运营后台。

## 开发运行

```bash
cd shiori-admin-web
cp .env.example .env
pnpm install
pnpm dev
```

默认通过 Vite 代理转发网关：`http://127.0.0.1:8080`。

## 构建与测试

```bash
pnpm test:run
pnpm build
```

## Playwright 端到端

```bash
pnpm e2e:install
pnpm e2e
```

可选环境变量：

- `E2E_GATEWAY_BASE_URL`（默认 `http://127.0.0.1:8080`）
- `E2E_MYSQL_CONTAINER`（默认 `shiori-mysql`）
- `E2E_MYSQL_USER`（必填，建议使用 `MYSQL_OPS_USERNAME`）
- `E2E_MYSQL_PASSWORD`（必填，建议使用 `MYSQL_OPS_PASSWORD`）

## 路由

- `/login`
- `/users`
- `/products`
- `/orders`

仅 `ROLE_ADMIN` 用户可登录进入后台。
