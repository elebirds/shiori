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

## 路由

- `/login`
- `/users`
- `/products`
- `/orders`

仅 `ROLE_ADMIN` 用户可登录进入后台。
