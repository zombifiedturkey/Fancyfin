import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import { AnimatePresence, motion } from "motion/react";
import { useEffect, useRef, useState } from "react";
import { useIsAdmin, useRefreshItem } from "../lib/admin-queries";
import { EditDetailsDialog } from "./EditDetailsDialog";
import { IdentifyDialog } from "./IdentifyDialog";
import { ImageDialog } from "./ImageDialog";

const menuItem =
	"block w-full cursor-pointer rounded-lg px-3 py-2 text-left text-sm text-ink transition-colors hover:bg-glass-strong disabled:cursor-wait disabled:opacity-60";

/** Fix-up tools for a title. Only shown to users who are allowed to change the library. */
export function ManageMenu({
	item,
	itemId,
}: {
	item: BaseItemDto;
	itemId: string;
}) {
	const isAdmin = useIsAdmin();
	const refresh = useRefreshItem(itemId);
	const [open, setOpen] = useState(false);
	const [identifying, setIdentifying] = useState(false);
	const [editing, setEditing] = useState(false);
	const [artwork, setArtwork] = useState(false);
	const root = useRef<HTMLDivElement>(null);

	useEffect(() => {
		if (!open) return;
		const onPointerDown = (event: PointerEvent) => {
			if (root.current && !root.current.contains(event.target as Node)) {
				setOpen(false);
			}
		};
		document.addEventListener("pointerdown", onPointerDown);
		return () => document.removeEventListener("pointerdown", onPointerDown);
	}, [open]);

	if (!isAdmin.data) return null;
	const canIdentify = item.Type === "Movie" || item.Type === "Series";

	return (
		<div ref={root} className="relative">
			<button
				type="button"
				aria-expanded={open}
				aria-haspopup="true"
				onClick={() => setOpen((value) => !value)}
				className="glass flex h-11 cursor-pointer items-center gap-2 rounded-pill px-5 text-sm font-semibold text-ink transition-colors hover:bg-glass-strong"
			>
				Manage <span aria-hidden>▾</span>
			</button>

			<AnimatePresence>
				{open && (
					<motion.div
						initial={{ opacity: 0, y: -6 }}
						animate={{ opacity: 1, y: 0 }}
						exit={{ opacity: 0, y: -6 }}
						transition={{ duration: 0.15 }}
						className="absolute left-0 top-full z-30 mt-2 w-60 rounded-card border border-white/15 bg-[rgb(16_18_28/0.8)] p-2 shadow-card backdrop-blur-2xl"
					>
						<button
							type="button"
							className={menuItem}
							onClick={() => {
								setOpen(false);
								setEditing(true);
							}}
						>
							Edit details…
						</button>
						<button
							type="button"
							className={menuItem}
							onClick={() => {
								setOpen(false);
								setArtwork(true);
							}}
						>
							Artwork…
						</button>
						{canIdentify && (
							<button
								type="button"
								className={menuItem}
								onClick={() => {
									setOpen(false);
									setIdentifying(true);
								}}
							>
								Fix match…
							</button>
						)}
						<button
							type="button"
							className={menuItem}
							disabled={refresh.isPending}
							onClick={() => {
								setOpen(false);
								refresh.mutate();
							}}
						>
							Refresh metadata
						</button>
					</motion.div>
				)}
			</AnimatePresence>

			{(refresh.isPending || refresh.isSuccess || refresh.isError) && (
				<p className="absolute left-0 top-full mt-2 whitespace-nowrap text-xs text-ink-muted">
					{refresh.isPending
						? "Asking the server to refresh…"
						: refresh.isError
							? "Couldn't start the refresh."
							: "Refresh started. Updates appear shortly."}
				</p>
			)}

			{editing && (
				<EditDetailsDialog item={item} onClose={() => setEditing(false)} />
			)}
			{artwork && <ImageDialog item={item} onClose={() => setArtwork(false)} />}
			{identifying && (
				<IdentifyDialog item={item} onClose={() => setIdentifying(false)} />
			)}
		</div>
	);
}
