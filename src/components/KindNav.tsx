import { Link } from "@tanstack/react-router";
import { AnimatePresence, motion } from "motion/react";
import { useEffect, useRef, useState } from "react";
import { LIBRARY_COPY, type LibraryKind } from "../lib/library";
import { useGenres } from "../lib/queries";
import { GenreLink } from "./GenreLink";

const pill =
	"cursor-pointer rounded-pill px-4 py-2 text-sm font-medium transition-colors";
const tab = `${pill} glass text-ink-muted hover:text-ink data-[status=active]:bg-ink data-[status=active]:text-canvas`;

function GenresMenu({
	kind,
	current,
}: {
	kind: LibraryKind;
	current?: string;
}) {
	const [open, setOpen] = useState(false);
	const root = useRef<HTMLDivElement>(null);
	const genres = useGenres(kind);

	useEffect(() => {
		if (!open) return;
		const onPointerDown = (event: PointerEvent) => {
			if (root.current && !root.current.contains(event.target as Node)) {
				setOpen(false);
			}
		};
		const onKeyDown = (event: KeyboardEvent) => {
			if (event.key === "Escape") setOpen(false);
		};
		document.addEventListener("pointerdown", onPointerDown);
		document.addEventListener("keydown", onKeyDown);
		return () => {
			document.removeEventListener("pointerdown", onPointerDown);
			document.removeEventListener("keydown", onKeyDown);
		};
	}, [open]);

	return (
		<div ref={root} className="relative">
			<button
				type="button"
				aria-expanded={open}
				aria-haspopup="true"
				onClick={() => setOpen((value) => !value)}
				className={`${pill} ${
					current ? "bg-ink text-canvas" : "glass text-ink-muted hover:text-ink"
				}`}
			>
				{current ?? "Genres"} <span aria-hidden>▾</span>
			</button>

			<AnimatePresence>
				{open && (
					<motion.div
						initial={{ opacity: 0, y: -6, scale: 0.98 }}
						animate={{ opacity: 1, y: 0, scale: 1 }}
						exit={{ opacity: 0, y: -6, scale: 0.98 }}
						transition={{ duration: 0.15 }}
						className="absolute left-0 top-full z-30 mt-2 max-h-[60dvh] w-[min(34rem,calc(100vw-2rem))] overflow-y-auto rounded-card border border-glass-border bg-canvas-raised/95 p-3 shadow-card backdrop-blur-xl"
					>
						{genres.isPending ? (
							<p className="p-2 text-sm text-ink-muted">Loading genres…</p>
						) : genres.data && genres.data.length > 0 ? (
							<ul className="grid grid-cols-2 gap-1 sm:grid-cols-3">
								{genres.data.map((genre) => (
									<li key={genre}>
										<GenreLink
											kind={kind}
											genre={genre}
											onClick={() => setOpen(false)}
											className={`block rounded-lg px-3 py-2 text-sm transition-colors hover:bg-glass-strong ${
												genre === current ? "text-accent-strong" : "text-ink"
											}`}
										>
											{genre}
										</GenreLink>
									</li>
								))}
							</ul>
						) : (
							<p className="p-2 text-sm text-ink-muted">No genres found.</p>
						)}
					</motion.div>
				)}
			</AnimatePresence>
		</div>
	);
}

/** Browse / Library tabs plus the genres flyout, shared by every Movies and TV Shows page. */
export function KindNav({
	kind,
	genre,
}: {
	kind: LibraryKind;
	/** The genre being viewed, if any. Shown on the flyout button. */
	genre?: string;
}) {
	return (
		<nav
			aria-label={LIBRARY_COPY[kind].title}
			className="flex flex-wrap items-center gap-2"
		>
			{kind === "movies" ? (
				<>
					<Link to="/movies" activeOptions={{ exact: true }} className={tab}>
						Browse
					</Link>
					<Link to="/movies/library" className={tab}>
						Library
					</Link>
				</>
			) : (
				<>
					<Link to="/shows" activeOptions={{ exact: true }} className={tab}>
						Browse
					</Link>
					<Link to="/shows/library" className={tab}>
						Library
					</Link>
				</>
			)}
			<GenresMenu kind={kind} current={genre} />
		</nav>
	);
}
