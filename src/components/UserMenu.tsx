import { AnimatePresence, motion } from "motion/react";
import { useEffect, useRef, useState } from "react";
import { useAuth, useSession } from "../auth/AuthProvider";
import { UserAvatar } from "./UserAvatar";

/** Avatar button in the nav with a small menu: who you are, and sign out. */
export function UserMenu() {
	const session = useSession();
	const { signOut } = useAuth();
	const [open, setOpen] = useState(false);
	const rootRef = useRef<HTMLDivElement>(null);
	const buttonRef = useRef<HTMLButtonElement>(null);

	// Close on outside click or Escape.
	useEffect(() => {
		if (!open) return;

		const onPointerDown = (event: PointerEvent) => {
			if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
		};
		const onKeyDown = (event: KeyboardEvent) => {
			if (event.key === "Escape") {
				setOpen(false);
				buttonRef.current?.focus();
			}
		};

		document.addEventListener("pointerdown", onPointerDown);
		document.addEventListener("keydown", onKeyDown);
		return () => {
			document.removeEventListener("pointerdown", onPointerDown);
			document.removeEventListener("keydown", onKeyDown);
		};
	}, [open]);

	return (
		<div ref={rootRef} className="relative">
			<button
				ref={buttonRef}
				type="button"
				aria-haspopup="menu"
				aria-expanded={open}
				aria-label={`Account menu for ${session.userName}`}
				onClick={() => setOpen((value) => !value)}
				className="rounded-full ring-1 ring-white/10 transition-shadow hover:ring-2 hover:ring-white/40"
			>
				<UserAvatar
					userId={session.userId}
					name={session.userName}
					imageTag={session.primaryImageTag}
					className="size-9 text-xs"
				/>
			</button>

			<AnimatePresence>
				{open && (
					<motion.div
						role="menu"
						initial={{ opacity: 0, y: -6, scale: 0.97 }}
						animate={{ opacity: 1, y: 0, scale: 1 }}
						exit={{ opacity: 0, y: -6, scale: 0.97 }}
						transition={{ duration: 0.16, ease: [0.2, 0, 0, 1] }}
						className="glass absolute right-0 top-full z-50 mt-3 w-60 origin-top-right rounded-card bg-canvas-raised/90 p-2 shadow-card"
					>
						<div className="flex items-center gap-3 px-3 py-3">
							<UserAvatar
								userId={session.userId}
								name={session.userName}
								imageTag={session.primaryImageTag}
								className="size-10 text-sm"
							/>
							<div className="min-w-0">
								<p className="truncate text-sm font-medium">
									{session.userName}
								</p>
								<p className="text-xs text-ink-faint">
									Fancyfin v{__APP_VERSION__}
								</p>
							</div>
						</div>
						<div className="my-1 h-px bg-glass-border" />
						<button
							type="button"
							role="menuitem"
							onClick={() => {
								setOpen(false);
								void signOut();
							}}
							className="w-full rounded-xl px-3 py-2.5 text-left text-sm text-ink-muted transition-colors hover:bg-glass-strong hover:text-ink"
						>
							Sign out
						</button>
					</motion.div>
				)}
			</AnimatePresence>
		</div>
	);
}
