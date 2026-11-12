import { useSyncExternalStore } from "react";
import { requestPopOutWindow } from "./pip";

/**
 * The floating player window, if one is open. It lives here, not in the Player, because the Player
 * is rebuilt for every episode and every audio or subtitle change, and the window has to survive that.
 */
let current: Window | null = null;
const listeners = new Set<() => void>();

function set(next: Window | null) {
	current = next;
	for (const listener of listeners) listener();
}

function subscribe(listener: () => void) {
	listeners.add(listener);
	return () => {
		listeners.delete(listener);
	};
}

/** The open pop-out window, or null. */
export function usePopOut(): Window | null {
	return useSyncExternalStore(
		subscribe,
		() => current,
		() => null,
	);
}

/** Open the floating window. Call it from a click. */
export async function openPopOut(
	videoWidth: number,
	videoHeight: number,
): Promise<Window> {
	if (current) return current;
	const win = await requestPopOutWindow(videoWidth, videoHeight);
	// Closing the window, or its "back to tab" button, brings the player home.
	win.addEventListener(
		"pagehide",
		() => {
			if (current === win) set(null);
		},
		{ once: true },
	);
	set(win);
	return win;
}

/** Close the floating window and bring the player back to the page. */
export function closePopOut() {
	const win = current;
	set(null);
	win?.close();
}
