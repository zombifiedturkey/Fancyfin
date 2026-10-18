import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { hueFor } from "../lib/hue";
import { posterImage } from "../lib/images";
import { usePeek } from "../lib/peek";
import { useSearchResults } from "../lib/queries";
import { FadeImage } from "./FadeImage";

const MAX_PER_GROUP = 5;

function detail(item: BaseItemDto): string {
	const kind = item.Type === "Series" ? "TV show" : "Movie";
	const extra =
		item.Type === "Series" && item.ChildCount
			? item.ChildCount === 1
				? "1 season"
				: `${item.ChildCount} seasons`
			: item.ProductionYear
				? String(item.ProductionYear)
				: "";
	return extra ? `${kind} · ${extra}` : kind;
}

function Row({
	item,
	active,
	onPick,
	onHover,
}: {
	item: BaseItemDto;
	active: boolean;
	onPick: () => void;
	onHover: () => void;
}) {
	const title = item.Name ?? "Untitled";
	const src = posterImage(item, 120);
	const hue = hueFor(title);
	return (
		<li>
			<button
				type="button"
				onClick={onPick}
				onMouseEnter={onHover}
				className={`flex w-full cursor-pointer items-center gap-3 rounded-xl p-2 text-left transition-colors ${active ? "bg-white/10" : ""}`}
			>
				<span
					className="relative aspect-[2/3] w-10 shrink-0 overflow-hidden rounded-md border border-glass-border"
					style={{
						backgroundImage: `linear-gradient(145deg, hsl(${hue} 45% 28%), hsl(${(hue + 50) % 360} 50% 14%))`,
					}}
				>
					{src && (
						<FadeImage
							key={src}
							src={src}
							className="absolute inset-0 size-full object-cover"
						/>
					)}
				</span>
				<span className="min-w-0">
					<span className="block truncate text-sm font-medium">{title}</span>
					<span className="block truncate text-xs text-ink-muted">
						{detail(item)}
					</span>
				</span>
			</button>
		</li>
	);
}

/**
 * Magnifying glass in the nav that expands into a search box. Results drop down in a small glass panel
 * at the top right; picking one opens that title's floating detail panel.
 */
export function SearchBox({
	onOpenChange,
}: {
	onOpenChange?: (open: boolean) => void;
}) {
	const peek = usePeek();
	const [open, setOpenState] = useState(false);
	const [text, setText] = useState("");
	const [term, setTerm] = useState("");
	const [activeIndex, setActiveIndex] = useState(0);
	const wrapperRef = useRef<HTMLDivElement>(null);
	const panelRef = useRef<HTMLDivElement>(null);
	const inputRef = useRef<HTMLInputElement>(null);

	const setOpen = useCallback(
		(next: boolean) => {
			setOpenState(next);
			onOpenChange?.(next);
			if (!next) {
				setText("");
				setTerm("");
			}
		},
		[onOpenChange],
	);

	// Debounce typing so we don't search on every keystroke.
	useEffect(() => {
		const timer = window.setTimeout(() => {
			setTerm(text);
			setActiveIndex(0);
		}, 200);
		return () => window.clearTimeout(timer);
	}, [text]);

	// Focus the box when it opens.
	useEffect(() => {
		if (open) inputRef.current?.focus();
	}, [open]);

	// Press "/" anywhere to open search.
	useEffect(() => {
		const onKey = (event: KeyboardEvent) => {
			if (event.key !== "/" || event.metaKey || event.ctrlKey || event.altKey)
				return;
			const target = event.target as HTMLElement | null;
			if (target?.closest("input, textarea, select, [contenteditable]")) return;
			event.preventDefault();
			setOpen(true);
		};
		window.addEventListener("keydown", onKey);
		return () => window.removeEventListener("keydown", onKey);
	}, [setOpen]);

	// Click outside closes it.
	useEffect(() => {
		if (!open) return;
		const onDown = (event: MouseEvent) => {
			const target = event.target as Node;
			if (
				wrapperRef.current?.contains(target) ||
				panelRef.current?.contains(target)
			)
				return;
			setOpen(false);
		};
		document.addEventListener("mousedown", onDown);
		return () => document.removeEventListener("mousedown", onDown);
	}, [open, setOpen]);

	const results = useSearchResults(term);
	const showPanel = open && term.trim().length > 0;
	const movies = (results.data?.movies ?? []).slice(0, MAX_PER_GROUP);
	const shows = (results.data?.shows ?? []).slice(0, MAX_PER_GROUP);
	const flat = [...movies, ...shows];

	const pick = (item: BaseItemDto | undefined) => {
		if (!item?.Id) return;
		setOpen(false);
		peek.open(item.Id);
	};

	const onKeyDown = (event: React.KeyboardEvent) => {
		if (event.key === "Escape") {
			event.preventDefault();
			setOpen(false);
		} else if (event.key === "ArrowDown") {
			event.preventDefault();
			setActiveIndex((i) => Math.min(i + 1, Math.max(flat.length - 1, 0)));
		} else if (event.key === "ArrowUp") {
			event.preventDefault();
			setActiveIndex((i) => Math.max(i - 1, 0));
		} else if (event.key === "Enter") {
			event.preventDefault();
			pick(flat[activeIndex]);
		}
	};

	const heading =
		"px-2 pb-1 pt-2 text-xs font-medium uppercase tracking-[0.2em] text-ink-muted";

	return (
		<>
			<div
				ref={wrapperRef}
				className={`flex h-9 items-center overflow-hidden rounded-full transition-[width,background-color] duration-300 ease-out ${open ? "w-40 bg-white/10 sm:w-48 lg:w-64" : "w-9"}`}
			>
				<button
					type="button"
					aria-label={open ? "Close search" : "Search (press /)"}
					title="Search (press /)"
					onClick={() => setOpen(!open)}
					className="flex size-9 shrink-0 cursor-pointer items-center justify-center rounded-full text-ink-muted transition-colors hover:text-ink"
				>
					<svg
						aria-hidden
						viewBox="0 0 24 24"
						className="size-5"
						fill="none"
						stroke="currentColor"
						strokeWidth="2.2"
						strokeLinecap="round"
					>
						<circle cx="11" cy="11" r="6.5" />
						<path d="M16 16l4.5 4.5" />
					</svg>
				</button>
				<input
					ref={inputRef}
					type="text"
					role="combobox"
					aria-expanded={showPanel}
					aria-controls="search-results"
					aria-label="Search movies and TV shows"
					tabIndex={open ? 0 : -1}
					value={text}
					onChange={(event) => setText(event.target.value)}
					onKeyDown={onKeyDown}
					placeholder="Search"
					autoComplete="off"
					spellCheck={false}
					className="min-w-0 flex-1 bg-transparent pr-3 text-sm text-ink outline-none placeholder:text-ink-faint"
				/>
			</div>

			{showPanel &&
				createPortal(
					<div
						ref={panelRef}
						id="search-results"
						className="glass fixed right-[var(--page-gutter)] top-[76px] z-[60] max-h-[min(34rem,calc(100dvh-6.5rem))] w-[clamp(19rem,26vw,28rem)] max-w-[calc(100vw-2rem)] overflow-y-auto rounded-card p-2 shadow-card"
					>
						{results.isError ? (
							<p className="p-3 text-sm text-ink-muted">
								Search failed. Try again in a moment.
							</p>
						) : results.isPending ? (
							<ul aria-hidden className="space-y-2 p-1">
								{Array.from({ length: 4 }, (_, i) => (
									<li key={i} className="shimmer h-16 rounded-xl" />
								))}
							</ul>
						) : flat.length === 0 ? (
							<p className="p-3 text-sm text-ink-muted">
								Nothing found for “{term.trim()}”.
							</p>
						) : (
							<div
								className={
									results.isPlaceholderData
										? "opacity-60 transition-opacity"
										: "transition-opacity"
								}
							>
								{movies.length > 0 && (
									<section>
										<h2 className={heading}>Movies</h2>
										<ul>
											{movies.map((item, i) => (
												<Row
													key={item.Id}
													item={item}
													active={activeIndex === i}
													onPick={() => pick(item)}
													onHover={() => setActiveIndex(i)}
												/>
											))}
										</ul>
									</section>
								)}
								{shows.length > 0 && (
									<section>
										<h2 className={heading}>TV Shows</h2>
										<ul>
											{shows.map((item, i) => (
												<Row
													key={item.Id}
													item={item}
													active={activeIndex === movies.length + i}
													onPick={() => pick(item)}
													onHover={() => setActiveIndex(movies.length + i)}
												/>
											))}
										</ul>
									</section>
								)}
							</div>
						)}
					</div>,
					document.body,
				)}
		</>
	);
}
