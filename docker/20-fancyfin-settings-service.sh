#!/bin/sh
# Starts the small settings service that keeps the Radarr and Sonarr connection saved from Settings.
# The nginx image runs this at start-up (everything in /docker-entrypoint.d/), then starts nginx.
set -eu

mkdir -p "${CONFIG_DIR:-/config}" 2>/dev/null || true

# Run in the background, with its messages going to the container log.
nohup node /opt/fancyfin/arr-server.mjs > /proc/1/fd/1 2> /proc/1/fd/2 &
