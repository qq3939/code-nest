# 多阶段构建：Node 只用于打包，运行时镜像只有 Nginx 和静态文件
FROM node:22-alpine AS build
WORKDIR /app
# 国内网络可以在 .env 里设置 NPM_REGISTRY=https://registry.npmmirror.com
ARG NPM_REGISTRY=https://registry.npmjs.org
COPY package.json package-lock.json ./
RUN npm config set registry "$NPM_REGISTRY" && npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
# Nginx 配置由 docker-compose 挂载 deploy/nginx/nginx.conf，这里只放静态文件
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
