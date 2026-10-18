import { Jellyfin } from "@jellyfin/sdk";
import { API_BASE, getServerUrl } from "./api-base";

const DEVICE_ID_KEY = "fancyfin.deviceId";

/** A stable per-browser device id, so the server shows one session per browser. */
function getDeviceId(): string {
	try {
		const existing = localStorage.getItem(DEVICE_ID_KEY);
		if (existing) return existing;
		const id = crypto.randomUUID();
		localStorage.setItem(DEVICE_ID_KEY, id);
		return id;
	} catch {
		return "fancyfin-web-unknown";
	}
}

/** The one place the Jellyfin SDK is configured. */
export const jellyfin = new Jellyfin({
	clientInfo: { name: "Fancyfin", version: __APP_VERSION__ },
	deviceInfo: { name: "Fancyfin Web", id: getDeviceId() },
});

/**
 * The shared SDK client. By default it talks to the same-origin /api proxy (no CORS); if the user entered a
 * server address on the connect screen it talks to that server directly. Changing the server reloads the page.
 * The access token is set and cleared with `api.update({ accessToken })` by the auth provider.
 */
export const api = jellyfin.createApi(
	getServerUrl() ?? `${window.location.origin}${API_BASE}`,
);
