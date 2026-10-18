export type Session = {
	userId: string;
	userName: string;
	accessToken: string;
	/** Present only when the user has a profile picture. */
	primaryImageTag?: string;
};

const STORAGE_KEY = "fancyfin.session";

function isSession(value: unknown): value is Session {
	if (typeof value !== "object" || value === null) return false;
	const v = value as Record<string, unknown>;
	return (
		typeof v.userId === "string" &&
		typeof v.userName === "string" &&
		typeof v.accessToken === "string" &&
		v.accessToken.length > 0 &&
		(v.primaryImageTag === undefined || typeof v.primaryImageTag === "string")
	);
}

/** The saved session, or null if there is none or it is unreadable. */
export function loadSession(): Session | null {
	try {
		const raw = localStorage.getItem(STORAGE_KEY);
		if (!raw) return null;
		const parsed: unknown = JSON.parse(raw);
		return isSession(parsed) ? parsed : null;
	} catch {
		return null;
	}
}

export function saveSession(session: Session): void {
	try {
		localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
	} catch {
		// Storage can be unavailable (private mode, blocked site data). The user just has to sign in again next time.
	}
}

export function clearSession(): void {
	try {
		localStorage.removeItem(STORAGE_KEY);
	} catch {
		// Nothing to clear if storage is unavailable.
	}
}
