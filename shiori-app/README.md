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
- `VITE_NOTIFY_WS_BASE_URL` 默认 `ws://localhost:8090/ws`

## 常用命令

```bash
pnpm dev
pnpm test:run
pnpm build
```
