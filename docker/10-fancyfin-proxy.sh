#!/bin/sh
# Runs when the container starts (the nginx image runs everything in /docker-entrypoint.d/).
# JELLYFIN_URL set   -> proxy /api/ to it, same-origin, no CORS.
# JELLYFIN_URL empty -> no proxy; the app asks the user for the server address instead.
set -eu

conf=/etc/nginx/fancyfin-proxy.conf
runtime=/usr/share/nginx/html/config.json

url="${JELLYFIN_URL:-}"
url="${url%/}"

if [ -n "$url" ]; then
    escaped=$(printf '%s' "$url" | sed 's/[&|\\]/\\&/g')
    sed "s|__JELLYFIN_URL__|$escaped|g" /etc/fancyfin/proxy.conf.template > "$conf"
    printf '{"proxy":true}\n' > "$runtime"
    echo "fancyfin: proxying /api/ to $url"
else
    printf 'location /api/ { return 404; }\n' > "$conf"
    printf '{"proxy":false}\n' > "$runtime"
    echo "fancyfin: JELLYFIN_URL not set; the app will ask for the server address"
fi
