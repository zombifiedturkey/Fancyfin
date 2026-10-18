import { useReducedMotion } from "motion/react";
import { useSyncExternalStore } from "react";
import {
	cssVars,
	DEFAULT_SETTINGS,
	type Settings,
	sanitizeSettings,
} from "./settings-model";

const STORAGE_KEY = "fancyfin.settings";

function load(): Settings {
	try {
		const raw = localStorage.getItem(STORAGE_KEY);
		return sanitizeSettings(raw ? JSON.parse(raw) : undefined);
	} catch {
		return sanitizeSettings(undefined);
	}
}

function save(settings: Settings) {
	try {
		localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
	} catch {
		// Private window or blocked storage: the settings still work until the tab closes.
	}
}

/** Put the settings on the page's root element, where the CSS picks them up. */
function apply(settings: Settings) {
	const root = document.documentElement;
	for (const [name, value] of Object.entries(cssVars(settings))) {
		root.style.setProperty(name, value);
	}
	root.dataset.motion = settings.motion;
}

let current = load();
const listeners = new Set<() => void>();

/** Call once before the first render so the page never flashes the default look. */
export function initSettings() {
	apply(current);
}

export function getSettings(): Settings {
	return current;
}

export function updateSettings(patch: Partial<Settings>) {
	current = sanitizeSettings({ ...current, ...patch });
	save(current);
	apply(current);
	for (const listener of listeners) listener();
}

export function resetSettings() {
	updateSettings({ ...DEFAULT_SETTINGS });
}

function subscribe(listener: () => void) {
	listeners.add(listener);
	return () => {
		listeners.delete(listener);
	};
}

/** The current settings. Components re-render when they change. */
export function useSettings(): Settings {
	return useSyncExternalStore(subscribe, getSettings, getSettings);
}

/** True when animation should be kept to a minimum: the system asks for it, or the person chose it. */
export function useMotionReduced(): boolean {
	const system = useReducedMotion();
	const { motion } = useSettings();
	return motion === "reduced" || Boolean(system);
}
