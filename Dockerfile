# syntax=docker/dockerfile:1

# ---- Build the static app ----
FROM node:22-alpine AS build
WORKDIR /app
RUN npm install -g pnpm@10
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

# ---- Serve it with nginx, proxying /api to Jellyfin ----
FROM nginx:stable-alpine
# The official nginx image runs envsubst over /etc/nginx/templates/*.template at startup,
# so ${JELLYFIN_URL} below is filled from the container's environment.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

ENV JELLYFIN_URL=http://jellyfin:8096
EXPOSE 80
