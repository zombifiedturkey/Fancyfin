# Shared by the Radarr and Sonarr locations. $arr_origin, $arr_host, $arr_base and $arr_key are set
# by each location just before this file is included.
auth_request /_fancyfin/admin;

# Drop the /radarr or /sonarr prefix and add the app's own URL base, if it has one.
rewrite ^/[a-z]+/(.*)$ $arr_base/$1 break;
proxy_pass $arr_origin;
proxy_ssl_server_name on;
proxy_http_version 1.1;
proxy_set_header Host $arr_host;
proxy_set_header X-Api-Key $arr_key;
proxy_set_header X-Emby-Token "";
proxy_set_header Accept "application/json";

proxy_connect_timeout 10s;
proxy_read_timeout 60s;
client_max_body_size 1m;
add_header Cache-Control "no-store";
