import type { BaseItemKind } from "@jellyfin/sdk/lib/generated-client/models/base-item-kind";
import type { ItemSortBy } from "@jellyfin/sdk/lib/generated-client/models/item-sort-by";
import type { SortOrder } from "@jellyfin/sdk/lib/generated-client/models/sort-order";

export type LibraryKind = "movies" | "shows";

export type SortKey = "name" | "added" | "released" | "rating";
export type WatchFilter = "all" | "unwatched" | "watched" | "favorites";

export type LibraryPrefs = {
	sort: SortKey;
	descending: boolean;
	filter: WatchFilter;
	/** A genre name, or "" for all genres. */
	genre: string;
};

type SortOption = {
	key: SortKey;
	label: string;
	sortBy: ItemSortBy;
	/** The direction people expect first: newest and best first, A-Z for names. */
	defaultDescending: boolean;
};

export const SORT_OPTIONS: SortOption[] = [
	{ key: "name", label: "Name", sortBy: "SortName", defaultDescending: false },
	{
		key: "added",
		label: "Date added",
		sortBy: "DateCreated",
		defaultDescending: true,
	},
	{
		key: "released",
		label: "Release date",
		sortBy: "PremiereDate",
		defaultDescending: true,
	},
	{
		key: "rating",
		label: "Rating",
		sortBy: "CommunityRating",
		defaultDescending: true,
	},
];

export const FILTER_OPTIONS: { key: WatchFilter; label: string }[] = [
	{ key: "all", label: "All" },
	{ key: "unwatched", label: "Unwatched" },
	{ key: "watched", label: "Watched" },
	{ key: "favorites", label: "Favorites" },
];

export const DEFAULT_PREFS: LibraryPrefs = {
	sort: "added",
	descending: true,
	filter: "all",
	genre: "",
};

export const LIBRARY_COPY: Record<
	LibraryKind,
	{ title: string; itemType: BaseItemKind; noun: string }
> = {
	movies: { title: "Movies", itemType: "Movie", noun: "movie" },
	shows: { title: "TV Shows", itemType: "Series", noun: "show" },
};

/**
 * Genres to always offer, even when no title in the library has them. TMDB has no Horror genre for
 * TV, so TV libraries often have none either, but it is a category people look for.
 */
const ALWAYS_GENRES: Record<LibraryKind, string[]> = {
	movies: [],
	shows: ["Horror"],
};

/** The server's genres plus the always-offered ones, sorted by name, each once. */
export function withAlwaysGenres(kind: LibraryKind, names: string[]): string[] {
	const have = new Set(names.map((name) => name.toLowerCase()));
	const extra = ALWAYS_GENRES[kind].filter((name) => !have.has(name.toLowerCase()));
	return [...names, ...extra].sort((a, b) => a.localeCompare(b));
}

/** Defaults for a sort key, used when the user picks a new sort. */
export function sortDefaults(key: SortKey): boolean {
	return SORT_OPTIONS.find((o) => o.key === key)?.defaultDescending ?? true;
}

/** Turns anything (old or corrupt saved data included) into valid preferences. */
export function sanitizePrefs(value: unknown): LibraryPrefs {
	if (typeof value !== "object" || value === null) return DEFAULT_PREFS;
	const raw = value as Record<string, unknown>;

	const sort = SORT_OPTIONS.find((o) => o.key === raw.sort)?.key;
	const filter = FILTER_OPTIONS.find((o) => o.key === raw.filter)?.key;

	return {
		sort: sort ?? DEFAULT_PREFS.sort,
		descending:
			typeof raw.descending === "boolean"
				? raw.descending
				: DEFAULT_PREFS.descending,
		filter: filter ?? DEFAULT_PREFS.filter,
		genre: typeof raw.genre === "string" ? raw.genre : DEFAULT_PREFS.genre,
	};
}

const storageKey = (kind: LibraryKind) => `fancyfin.library.${kind}`;

export function loadPrefs(kind: LibraryKind): LibraryPrefs {
	try {
		const text = localStorage.getItem(storageKey(kind));
		return text ? sanitizePrefs(JSON.parse(text)) : DEFAULT_PREFS;
	} catch {
		return DEFAULT_PREFS;
	}
}

export function savePrefs(kind: LibraryKind, prefs: LibraryPrefs): void {
	try {
		localStorage.setItem(storageKey(kind), JSON.stringify(prefs));
	} catch {
		// Storage unavailable: the choice just won't be remembered.
	}
}

/** The server-side query for a set of preferences. */
export function buildItemsQuery(kind: LibraryKind, prefs: LibraryPrefs) {
	const primary =
		SORT_OPTIONS.find((o) => o.key === prefs.sort) ?? SORT_OPTIONS[0];
	const direction: SortOrder = prefs.descending ? "Descending" : "Ascending";

	// Sorting by name alone is already unique; anything else gets a name tiebreak
	// so pages never shuffle items between requests.
	const byName = primary?.sortBy === "SortName";
	const sortBy: ItemSortBy[] = byName
		? ["SortName"]
		: [primary?.sortBy ?? "SortName", "SortName"];
	const sortOrder: SortOrder[] = byName
		? [direction]
		: [direction, "Ascending"];

	return {
		includeItemTypes: [LIBRARY_COPY[kind].itemType],
		recursive: true,
		sortBy,
		sortOrder,
		isPlayed:
			prefs.filter === "unwatched"
				? false
				: prefs.filter === "watched"
					? true
					: undefined,
		isFavorite: prefs.filter === "favorites" ? true : undefined,
		genres: prefs.genre ? [prefs.genre] : undefined,
	};
}

/** A row of titles on a browse page. */
export type ShelfSpec = {
	label: string;
	sortBy: ItemSortBy;
	descending: boolean;
	/** Leave out titles that haven't been released yet. */
	releasedOnly?: boolean;
};

export const SHELVES = {
	recent: {
		label: "Recently Added",
		sortBy: "DateCreated",
		descending: true,
	},
	released: {
		label: "New Releases",
		sortBy: "PremiereDate",
		descending: true,
		releasedOnly: true,
	},
	rated: {
		label: "Top Rated",
		sortBy: "CommunityRating",
		descending: true,
	},
} satisfies Record<string, ShelfSpec>;

export type ShelfKey = keyof typeof SHELVES;
