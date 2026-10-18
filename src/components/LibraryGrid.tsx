import { useEffect, useRef, useState } from "react";
import {
	DEFAULT_PREFS,
	FILTER_OPTIONS,
	LIBRARY_COPY,
	type LibraryKind,
	type LibraryPrefs,
	loadPrefs,
	SORT_OPTIONS,
	type SortKey,
	savePrefs,
	sortDefaults,
	type WatchFilter,
} from "../lib/library";
import { useGenres, useLibraryItems } from "../lib/queries";
import { MediaCard } from "./MediaCard";

const selectClass =
	"glass cursor-pointer rounded-pill px-4 py-2 text-sm text-ink outline-none";

/** The grid shape for poster cards: as many columns as fit. */
const gridClass =
	"grid grid-cols-[repeat(auto-fill,minmax(8.5rem,1fr))] gap-x-4 gap-y-8 sm:gap-x-5";

export function LibraryGrid({
	kind,
	fixedGenre,
}: {
	kind: LibraryKind;
	/** Lock the grid to one genre (used on genre pages). Its choices are not saved. */
	fixedGenre?: string;
}) {
	const copy = LIBRARY_COPY[kind];
	const [prefs, setPrefs] = useState<LibraryPrefs>(() =>
		fixedGenre
			? { ...DEFAULT_PREFS, sort: "name", descending: false, genre: fixedGenre }
			: loadPrefs(kind),
	);

	useEffect(() => {
		if (!fixedGenre) savePrefs(kind, prefs);
	}, [kind, prefs, fixedGenre]);

	const update = (patch: Partial<LibraryPrefs>) =>
		setPrefs((current) => ({ ...current, ...patch }));

	const query = useLibraryItems(kind, prefs);
	const genres = useGenres(kind);

	const items = query.data?.pages.flatMap((page) => page.items) ?? [];
	const total = query.data?.pages[0]?.total;
	const filtered =
		prefs.filter !== "all" || (!fixedGenre && prefs.genre !== "");

	// Load the next page shortly before the user reaches the bottom.
	const sentinel = useRef<HTMLDivElement>(null);
	const { hasNextPage, isFetchingNextPage, isPlaceholderData, fetchNextPage } =
		query;
	useEffect(() => {
		const element = sentinel.current;
		if (!element || !hasNextPage || isPlaceholderData) return;
		const observer = new IntersectionObserver(
			(entries) => {
				if (
					entries.some((entry) => entry.isIntersecting) &&
					!isFetchingNextPage
				) {
					void fetchNextPage();
				}
			},
			{ rootMargin: "800px" },
		);
		observer.observe(element);
		return () => observer.disconnect();
	}, [hasNextPage, isFetchingNextPage, isPlaceholderData, fetchNextPage]);

	const genreNames = [...(genres.data ?? [])];
	if (prefs.genre && !genreNames.includes(prefs.genre)) {
		genreNames.push(prefs.genre);
	}

	return (
		<div>
			<p className="h-5 text-sm text-ink-muted">
				{total !== undefined &&
					`${total.toLocaleString()} ${copy.noun}${total === 1 ? "" : "s"}${
						filtered ? " match" : ""
					}`}
			</p>

			<div className="mt-4 flex flex-wrap items-center gap-3">
				<div className="flex items-center gap-2">
					<select
						aria-label="Sort by"
						className={selectClass}
						value={prefs.sort}
						onChange={(event) => {
							const sort = event.target.value as SortKey;
							update({ sort, descending: sortDefaults(sort) });
						}}
					>
						{SORT_OPTIONS.map((option) => (
							<option key={option.key} value={option.key}>
								{option.label}
							</option>
						))}
					</select>
					<button
						type="button"
						className="glass size-9 cursor-pointer rounded-pill text-sm"
						aria-label={
							prefs.descending ? "Sorted descending" : "Sorted ascending"
						}
						title={prefs.descending ? "Descending" : "Ascending"}
						onClick={() => update({ descending: !prefs.descending })}
					>
						{prefs.descending ? "↓" : "↑"}
					</button>
				</div>

				<div className="flex flex-wrap items-center gap-2">
					{FILTER_OPTIONS.map((option) => (
						<button
							key={option.key}
							type="button"
							aria-pressed={prefs.filter === option.key}
							onClick={() => update({ filter: option.key as WatchFilter })}
							className={`cursor-pointer rounded-pill px-4 py-2 text-sm font-medium transition-colors ${
								prefs.filter === option.key
									? "bg-ink text-canvas"
									: "glass text-ink-muted hover:text-ink"
							}`}
						>
							{option.label}
						</button>
					))}
				</div>

				{!fixedGenre && (
					<select
						aria-label="Genre"
						className={selectClass}
						value={prefs.genre}
						onChange={(event) => update({ genre: event.target.value })}
					>
						<option value="">All genres</option>
						{genreNames.map((name) => (
							<option key={name} value={name}>
								{name}
							</option>
						))}
					</select>
				)}
			</div>

			<div className="mt-8">
				{query.isPending ? (
					<ul className={gridClass} aria-hidden>
						{Array.from({ length: 24 }, (_, i) => (
							<li key={i}>
								<div className="shimmer aspect-[2/3] rounded-card border border-glass-border" />
							</li>
						))}
					</ul>
				) : query.isError && items.length === 0 ? (
					<div className="py-16">
						<p className="text-ink-muted">
							Couldn't load your {copy.title.toLowerCase()}.
						</p>
						<button
							type="button"
							className="glass mt-4 cursor-pointer rounded-pill px-5 py-2 text-sm font-medium"
							onClick={() => void query.refetch()}
						>
							Try again
						</button>
					</div>
				) : items.length === 0 ? (
					<div className="py-16">
						<p className="text-ink-muted">
							{filtered
								? `No ${copy.noun}s match these filters.`
								: `No ${copy.noun}s in your library yet.`}
						</p>
						{filtered && (
							<button
								type="button"
								className="glass mt-4 cursor-pointer rounded-pill px-5 py-2 text-sm font-medium"
								onClick={() =>
									update({ filter: "all", genre: fixedGenre ?? "" })
								}
							>
								Clear filters
							</button>
						)}
					</div>
				) : (
					<ul
						className={`${gridClass} transition-opacity duration-200 ${
							isPlaceholderData ? "opacity-50" : "opacity-100"
						}`}
					>
						{items.map((item) => (
							<li key={item.Id}>
								<MediaCard item={item} variant="poster" />
							</li>
						))}
					</ul>
				)}

				<div ref={sentinel} aria-hidden className="h-px" />
				{isFetchingNextPage && (
					<p className="mt-8 text-sm text-ink-muted">Loading more…</p>
				)}
			</div>
		</div>
	);
}
