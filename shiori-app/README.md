# shiori-app

Shiori 用户端 Web（Vue 3 + TypeScript + Vite + Tailwind CSS）。

## 启动

```bash
cd shiori-app
cp .env.example .env
pnpm install
pnpm dev
```

## 环境变量

- `VITE_API_BASE_URL` 默认 `http://localhost:8080`
- `VITE_NOTIFY_WS_BASE_URL` 默认 `ws://localhost:8080/ws`

## 常用命令

```bash
pnpm dev
pnpm test:run
pnpm build
pnpm e2e
```

## Playwright E2E 烟测

前置条件：
- 网关与后端服务已启动（`http://localhost:8080`）
- gateway 已启动并可转发 WS（`ws://localhost:8080/ws`）

首次运行需安装浏览器：

```bash
pnpm e2e:install
```

执行烟测：

```bash
pnpm e2e
```
