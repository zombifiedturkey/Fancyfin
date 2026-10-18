#!/bin/sh
# Runs when the container starts (the nginx image runs everything in /docker-entrypoint.d/).
# JELLYFIN_URL set   -> proxy /api/ to it, same-origin, no CORS.
# JELLYFIN_URL empty -> no proxy; the app asks the user for the server address instead.
# (The TMDB, Radarr and Sonarr connections are saved from Settings, not here.)
set -eu

conf=/etc/nginx/fancyfin-proxy.conf
runtime=/usr/share/nginx/html/config.json
templates=/etc/fancyfin

url="${JELLYFIN_URL:-}"
url="${url%/}"

if [ -n "$url" ]; then
    escaped=$(printf '%s' "$url" | sed 's/[&|\\]/\\&/g')
    sed "s|__JELLYFIN_URL__|$escaped|g" "$templates/proxy.conf.template" > "$conf"
    proxy=true
    echo "fancyfin: proxying /api/ to $url"
else
    printf 'location /api/ { return 404; }\n' > "$conf"
    proxy=false
    echo "fancyfin: JELLYFIN_URL not set; the app will ask for the server address"
fi

printf '{"proxy":%s}\n' "$proxy" > "$runtime"
