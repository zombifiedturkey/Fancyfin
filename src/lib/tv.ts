/** What the Android TV app offers the page, through window.FancyfinTV. */
export type TvBridge = {
	version(): string;
	checkForUpdates(): void;
	changeServer(): void;
};

declare global {
	interface Window {
		FancyfinTV?: TvBridge;
		/** Called by the TV app when Back is pressed. True means the page used it (closed something). */
		fancyfinBack?: () => boolean;
	}
}

/** The Android TV app adds "FancyfinTV/<version>" to the browser's name. */
export function isTvUserAgent(userAgent: string): boolean {
	return /FancyfinTV\//.test(userAgent);
}

/**
 * True in the Android TV app. Adding ?tv=1 to the address turns the same layout on in a normal
 * browser (handy for trying it with the arrow keys), and that choice is kept for the visit.
 */
export function isTv(): boolean {
	if (isTvUserAgent(navigator.userAgent)) return true;
	try {
		if (new URLSearchParams(window.location.search).get("tv") === "1") {
			sessionStorage.setItem("fancyfin.tv", "1");
		}
		return sessionStorage.getItem("fancyfin.tv") === "1";
	} catch {
		return false;
	}
}

/** The TV app's hooks, when this page is running inside it. */
export function getTvBridge(): TvBridge | undefined {
	return typeof window === "undefined" ? undefined : window.FancyfinTV;
}
