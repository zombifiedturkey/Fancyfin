#!/bin/sh
# Runs when the container starts (the nginx image runs everything in /docker-entrypoint.d/).
# JELLYFIN_URL set   -> proxy /api/ to it, same-origin, no CORS.
# JELLYFIN_URL empty -> no proxy; the app asks the user for the server address instead.
# TMDB_API_KEY set   -> proxy /tmdb/ to TMDB with that key (it stays on the server).
# TMDB_API_KEY empty -> no TMDB; the featured banner shows recently added titles.
# RADARR_URL + RADARR_API_KEY (and/or SONARR_...) set, with JELLYFIN_URL
#                    -> proxy /radarr/ (/sonarr/) for Jellyfin administrators, keys kept on the server.
set -eu

conf=/etc/nginx/fancyfin-proxy.conf
tmdb_conf=/etc/nginx/fancyfin-tmdb.conf
arr_conf=/etc/nginx/fancyfin-arr.conf
runtime=/usr/share/nginx/html/config.json
templates=/etc/fancyfin

url="${JELLYFIN_URL:-}"
url="${url%/}"

# The DNS server nginx should use to look up names while it is running.
resolver=$(awk '/^nameserver/ { print $2; exit }' /etc/resolv.conf 2>/dev/null || true)
[ -n "$resolver" ] || resolver=1.1.1.1
case "$resolver" in *:*) resolver="[$resolver]" ;; esac

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

key="${TMDB_API_KEY:-}"
# Keys are letters, digits and . _ - only. Anything else would break the nginx config.
case "$key" in
    *[!A-Za-z0-9._-]*)
        echo "fancyfin: TMDB_API_KEY has unexpected characters; ignoring it"
        key=""
        ;;
esac

if [ -n "$key" ]; then
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
        "$templates/tmdb.conf.template" > "$tmdb_conf"
    tmdb=true
    echo "fancyfin: proxying /tmdb/ to TMDB"
else
    printf 'location /tmdb/ { return 404; }\n' > "$tmdb_conf"
    tmdb=false
    echo "fancyfin: TMDB_API_KEY not set; the featured banner will show recently added titles"
fi

# ---- Radarr and Sonarr ----
# add_arr NAME KIND URL KEY appends one app's proxy config to $arr_body and succeeds if it was valid.
# NAME is radarr or sonarr; KIND is the API's word for a title (movie or series).
arr_body=$(mktemp)

add_arr() {
    name=$1
    kind=$2
    arr_url="${3:-}"
    arr_key="${4:-}"
    arr_url="${arr_url%/}"

    if [ -z "$arr_url" ] && [ -z "$arr_key" ]; then
        return 1
    fi
    if [ -z "$arr_url" ] || [ -z "$arr_key" ]; then
        echo "fancyfin: $name needs both its URL and its API key; skipping it" >&2
        return 1
    fi
    case "$arr_url" in
        http://* | https://*) ;;
        *)
            echo "fancyfin: the $name URL must start with http:// or https://; skipping it" >&2
            return 1
            ;;
    esac
    case "$arr_url" in
        *[!A-Za-z0-9._:/-]*)
            echo "fancyfin: the $name URL has unexpected characters; skipping it" >&2
            return 1
            ;;
    esac
    case "$arr_key" in
        *[!A-Za-z0-9._-]*)
            echo "fancyfin: the $name API key has unexpected characters; skipping it" >&2
            return 1
            ;;
    esac

    # Split http://host:7878/base into the address (http://host:7878) and the app's URL base (/base).
    arr_origin=$(printf '%s' "$arr_url" | sed -E 's|^(https?://[^/]+).*|\1|')
    arr_base="${arr_url#"$arr_origin"}"
    arr_host="${arr_origin#*://}"

    sed -e "s|__APP__|$name|g" \
        -e "s|__KIND__|$kind|g" \
        -e "s|__ORIGIN__|$arr_origin|g" \
        -e "s|__HOST__|$arr_host|g" \
        -e "s|__BASE__|$arr_base|g" \
        -e "s|__KEY__|$arr_key|g" \
        "$templates/arr.conf.template" >> "$arr_body"
    echo "fancyfin: proxying /$name/ to $arr_origin$arr_base (administrators only)"
    return 0
}

radarr=false
sonarr=false
if [ -n "$url" ]; then
    if add_arr radarr movie "${RADARR_URL:-}" "${RADARR_API_KEY:-}"; then radarr=true; fi
    if add_arr sonarr series "${SONARR_URL:-}" "${SONARR_API_KEY:-}"; then sonarr=true; fi
elif [ -n "${RADARR_URL:-}${SONARR_URL:-}" ]; then
    # Who is an administrator is decided by Jellyfin, so without its address nobody can be let in.
    echo "fancyfin: Radarr/Sonarr need JELLYFIN_URL to check who is an administrator; leaving them off"
fi

if [ "$radarr" = true ] || [ "$sonarr" = true ]; then
    escaped=${escaped:-}
    {
        sed -e "s|__RESOLVER__|$resolver|g" \
            -e "s|__JELLYFIN_URL__|$escaped|g" \
            "$templates/arr-admin.conf.template"
        cat "$arr_body"
    } > "$arr_conf"
else
    printf '# Radarr and Sonarr are not set up.\n' > "$arr_conf"
fi
rm -f "$arr_body"

printf '{"proxy":%s,"tmdb":%s,"radarr":%s,"sonarr":%s}\n' \
    "$proxy" "$tmdb" "$radarr" "$sonarr" > "$runtime"
