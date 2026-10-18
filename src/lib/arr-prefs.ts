import { useSyncExternalStore } from "react";
import {
	type AllPrefs,
	type ArrApp,
	type ArrPrefs,
	sanitizePrefs,
} from "./arr";

const STORAGE_KEY = "fancyfin.arr";

function load(): AllPrefs {
	try {
		const raw = localStorage.getItem(STORAGE_KEY);
		return sanitizePrefs(raw ? JSON.parse(raw) : undefined);
	} catch {
		return sanitizePrefs(undefined);
	}
}

let current = load();
const listeners = new Set<() => void>();

export function getArrPrefs(): AllPrefs {
	return current;
}

/** Remember the choices for one app: folder, profile and so on, and whether to skip the dialog. */
export function saveArrPrefs(app: ArrApp, prefs: ArrPrefs) {
	current = sanitizePrefs({ ...current, [app]: prefs });
	try {
		localStorage.setItem(STORAGE_KEY, JSON.stringify(current));
	} catch {
		// Private window or blocked storage: it still works until the tab closes.
	}
	for (const listener of listeners) listener();
}

function subscribe(listener: () => void) {
	listeners.add(listener);
	return () => {
		listeners.delete(listener);
	};
}

/** What was chosen last time for Radarr and Sonarr. Components re-render when it changes. */
export function useArrPrefs(): AllPrefs {
	return useSyncExternalStore(subscribe, getArrPrefs, getArrPrefs);
}
