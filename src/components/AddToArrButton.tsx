import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { ARR_LABEL } from "../lib/arr";
import { useArrPrefs } from "../lib/arr-prefs";
import {
	arrForKind,
	describeArrError,
	quickAdd,
	useArrAdded,
	useArrAvailable,
} from "../lib/arr-queries";
import type { TmdbTitle } from "../lib/tmdb";
import { AddToArrDialog } from "./AddToArrDialog";

const styles = {
	hero: "flex cursor-pointer items-center gap-2 rounded-pill bg-ink px-6 py-3 text-sm font-semibold text-canvas transition-opacity hover:opacity-90 disabled:cursor-default disabled:opacity-60",
	row: "glass flex shrink-0 cursor-pointer items-center gap-1.5 rounded-pill px-3 py-1.5 text-xs font-semibold text-ink transition-colors hover:bg-glass-strong disabled:cursor-default disabled:opacity-70",
	card: "glass flex w-full cursor-pointer items-center justify-center gap-1.5 rounded-pill px-3 py-1.5 text-xs font-semibold text-ink transition-colors hover:bg-glass-strong disabled:cursor-default disabled:opacity-70",
} as const;

const addedStyles = {
	hero: "glass flex items-center gap-2 rounded-pill px-6 py-3 text-sm font-semibold text-ink",
	row: "glass flex shrink-0 items-center gap-1.5 rounded-pill px-3 py-1.5 text-xs font-semibold text-success",
	card: "glass flex w-full items-center justify-center gap-1.5 rounded-pill px-3 py-1.5 text-xs font-semibold text-success",
} as const;

type AddToArrButtonProps = {
	title: TmdbTitle;
	variant: "hero" | "card" | "row";
	/** Told when the Add dialog opens or closes, so a carousel can stop moving while it's open. */
	onDialogChange?: (open: boolean) => void;
};

function Icon({ kind }: { kind: "plus" | "check" }) {
	return (
		<svg
			aria-hidden
			viewBox="0 0 24 24"
			className="size-4 shrink-0"
			fill="none"
			stroke="currentColor"
			strokeWidth="2.5"
			strokeLinecap="round"
			strokeLinejoin="round"
		>
			<path
				d={kind === "plus" ? "M12 5v14M5 12h14" : "M5 12.5l4.5 4.5L19 7.5"}
			/>
		</svg>
	);
}

function AddButton({ title, variant, onDialogChange }: AddToArrButtonProps) {
	const queryClient = useQueryClient();
	const app = arrForKind(title.kind);
	const label = ARR_LABEL[app];
	const prefs = useArrPrefs()[app];
	const added = useArrAdded(title);

	const [open, setOpen] = useState(false);
	const [notice, setNotice] = useState<string>();
	const [busy, setBusy] = useState(false);

	useEffect(() => {
		onDialogChange?.(open);
		return () => onDialogChange?.(false);
	}, [open, onDialogChange]);

	if (added) {
		return (
			<span className={addedStyles[variant]}>
				<Icon kind="check" />
				Added to {label}
			</span>
		);
	}

	const click = () => {
		setNotice(undefined);
		if (!prefs.skip) {
			setOpen(true);
			return;
		}
		// "Skip this window" is on: add with the saved settings. If that can't be done safely,
		// show the window with the reason instead.
		setBusy(true);
		quickAdd(queryClient, title)
			.catch((error: unknown) => {
				setNotice(describeArrError(error));
				setOpen(true);
			})
			.finally(() => setBusy(false));
	};

	return (
		<>
			<button
				type="button"
				onClick={click}
				disabled={busy}
				className={styles[variant]}
			>
				<Icon kind="plus" />
				{busy ? "Adding…" : variant === "row" ? "Add" : `Add to ${label}`}
			</button>
			{open && (
				<AddToArrDialog
					title={title}
					notice={notice}
					onClose={() => setOpen(false)}
				/>
			)}
		</>
	);
}

/**
 * "Add to Radarr" (movies) or "Add to Sonarr" (shows) for a title the library doesn't have.
 * Only appears for Jellyfin administrators, and only when that app is set up on the server.
 */
export function AddToArrButton(props: AddToArrButtonProps) {
	const available = useArrAvailable(arrForKind(props.title.kind));
	if (!available) return null;
	return <AddButton {...props} />;
}
