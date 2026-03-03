FROM node:22-alpine AS builder

WORKDIR /app
ENV PNPM_HOME=/pnpm
ENV PATH=$PNPM_HOME:$PATH
RUN corepack enable

COPY shiori-admin-web/package.json shiori-admin-web/pnpm-lock.yaml /app/
RUN pnpm install --frozen-lockfile

COPY shiori-admin-web/ /app/

ARG VITE_API_BASE_URL=/
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

RUN pnpm build

FROM nginx:1.27-alpine

COPY deploy/nginx/shiori-admin-web.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
