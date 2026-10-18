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
# At start-up, docker/10-fancyfin-proxy.sh turns the optional JELLYFIN_URL into the /api proxy config
# (without it the app asks for the Jellyfin address in the browser) and the optional TMDB_API_KEY
# into the /tmdb proxy config, and the optional RADARR_* / SONARR_* pairs into the /radarr and /sonarr
# proxy configs (administrators only).
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY nginx/proxy.conf.template /etc/fancyfin/proxy.conf.template
COPY nginx/tmdb.conf.template /etc/fancyfin/tmdb.conf.template
COPY nginx/arr-admin.conf.template /etc/fancyfin/arr-admin.conf.template
COPY nginx/arr.conf.template /etc/fancyfin/arr.conf.template
COPY nginx/arr-proxy.inc /etc/nginx/fancyfin-arr-proxy.inc
COPY docker/10-fancyfin-proxy.sh /docker-entrypoint.d/10-fancyfin-proxy.sh
RUN chmod +x /docker-entrypoint.d/10-fancyfin-proxy.sh
COPY --from=build /app/dist /usr/share/nginx/html

ENV JELLYFIN_URL=""
ENV TMDB_API_KEY=""
ENV RADARR_URL=""
ENV RADARR_API_KEY=""
ENV SONARR_URL=""
ENV SONARR_API_KEY=""
EXPOSE 80
