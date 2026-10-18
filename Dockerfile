# syntax=docker/dockerfile:1

# ---- Build the static app ----
FROM node:22-alpine AS build
WORKDIR /app
RUN npm install -g pnpm@10
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

# ---- Serve it with nginx ----
FROM nginx:stable-alpine
RUN apk add --no-cache nodejs
# At start-up, docker/10-fancyfin-proxy.sh turns the optional JELLYFIN_URL into the /api proxy config
# (without it the app asks for the Jellyfin address in the browser). docker/20-fancyfin-settings-service.sh
# starts the small Node service (server/arr-server.mjs) that keeps the TMDB, Radarr and Sonarr
# connections saved from Settings, and passes the TMDB calls on.
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY nginx/proxy.conf.template /etc/fancyfin/proxy.conf.template
COPY docker/10-fancyfin-proxy.sh /docker-entrypoint.d/10-fancyfin-proxy.sh
COPY docker/20-fancyfin-settings-service.sh /docker-entrypoint.d/20-fancyfin-settings-service.sh
COPY server/arr-server.mjs /opt/fancyfin/arr-server.mjs
RUN chmod +x /docker-entrypoint.d/10-fancyfin-proxy.sh /docker-entrypoint.d/20-fancyfin-settings-service.sh
COPY --from=build /app/dist /usr/share/nginx/html

ENV JELLYFIN_URL=""
ENV CONFIG_DIR=/config
VOLUME /config
EXPOSE 80
