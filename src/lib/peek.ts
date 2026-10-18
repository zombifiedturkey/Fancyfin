import { useNavigate } from "@tanstack/react-router";
import { useCallback } from "react";

/** Stays on the current page and changes only the ?peek= part of the address. */
function withPeek(peek: string | undefined) {
	return (prev: Record<string, unknown>) => ({ ...prev, peek });
}

/**
 * Open or close the floating detail panel. The panel lives in the URL (?peek=<id>),
 * so the Back button closes it and a refresh keeps it open.
 */
export function usePeek() {
	const navigate = useNavigate();

	// The router can't know which page we're on from here, so its search typing is
	// bypassed with a cast. The root route validates ?peek= itself.
	const open = useCallback(
		(id: string) => {
			void navigate({ search: withPeek(id) as never });
		},
		[navigate],
	);

	const close = useCallback(() => {
		void navigate({ search: withPeek(undefined) as never, replace: true });
	}, [navigate]);

	return { open, close };
}
