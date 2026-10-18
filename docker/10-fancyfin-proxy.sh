#!/bin/sh
# Runs when the container starts (the nginx image runs everything in /docker-entrypoint.d/).
# JELLYFIN_URL set   -> proxy /api/ to it, same-origin, no CORS.
# JELLYFIN_URL empty -> no proxy; the app asks the user for the server address instead.
# TMDB_API_KEY set   -> proxy /tmdb/ to TMDB with that key (it stays on the server).
# TMDB_API_KEY empty -> no TMDB; the featured banner shows recently added titles.
set -eu

conf=/etc/nginx/fancyfin-proxy.conf
tmdb_conf=/etc/nginx/fancyfin-tmdb.conf
runtime=/usr/share/nginx/html/config.json

url="${JELLYFIN_URL:-}"
url="${url%/}"

if [ -n "$url" ]; then
    escaped=$(printf '%s' "$url" | sed 's/[&|\\]/\\&/g')
    sed "s|__JELLYFIN_URL__|$escaped|g" /etc/fancyfin/proxy.conf.template > "$conf"
    proxy=true
    echo "fancyfin: proxying /api/ to $url"
else
    printf 'location /api/ { return 404; }\n' > "$conf"
    proxy=false
    echo "fancyfin: JELLYFIN_URL not set; the app will ask for the server address"
fi

key="${TMDB_API_KEY:-}"
# Keys are letters, digits and . _ - only. Anything else would break the nginx config.
case "$key" in
    *[!A-Za-z0-9._-]*)
        echo "fancyfin: TMDB_API_KEY has unexpected characters; ignoring it"
        key=""
        ;;
esac

if [ -n "$key" ]; then
    resolver=$(awk '/^nameserver/ { print $2; exit }' /etc/resolv.conf)
    [ -n "$resolver" ] || resolver=1.1.1.1
    case "$resolver" in *:*) resolver="[$resolver]" ;; esac

    # A "read access token" (contains dots) goes in a header; a plain API key goes in the address.
    case "$key" in
        *.*)
            auth="proxy_set_header Authorization \"Bearer $key\";"
            args=""
            ;;
        *)
            auth=""
            args="?api_key=$key"
            ;;
    esac

    sed -e "s|__RESOLVER__|$resolver|g" \
        -e "s|__REWRITE_ARGS__|$args|g" \
        -e "s|__AUTH__|$auth|g" \
        /etc/fancyfin/tmdb.conf.template > "$tmdb_conf"
    tmdb=true
    echo "fancyfin: proxying /tmdb/ to TMDB"
else
    printf 'location /tmdb/ { return 404; }\n' > "$tmdb_conf"
    tmdb=false
    echo "fancyfin: TMDB_API_KEY not set; the featured banner will show recently added titles"
fi

printf '{"proxy":%s,"tmdb":%s}\n' "$proxy" "$tmdb" > "$runtime"
