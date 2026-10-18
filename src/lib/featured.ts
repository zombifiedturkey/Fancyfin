import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import type { FeaturedSource } from "./settings-model";
import type { TmdbTitle } from "./tmdb";

/** One slide in the featured banner: a title from the library, or a popular one that isn't. */
export type FeaturedEntry =
	| { key: string; owned: BaseItemDto; popular: boolean }
	| { key: string; discover: TmdbTitle };

/** A popular title and, if the library has it, the library's copy. */
export type PopularMatch = { title: TmdbTitle; owned?: BaseItemDto };

export const FEATURED_LIMIT = 6;
/** With fewer popular entries than this, the banner is topped up from recently added. */
const MIN_POPULAR = 3;

/** a1, b1, a2, b2 ... then whatever is left of the longer list. */
export function interleave<T>(a: T[], b: T[]): T[] {
	const out: T[] = [];
	for (let i = 0; i < Math.max(a.length, b.length); i++) {
		const x = a[i];
		const y = b[i];
		if (x !== undefined) out.push(x);
		if (y !== undefined) out.push(y);
	}
	return out;
}

function dedupe(entries: FeaturedEntry[]): FeaturedEntry[] {
	const seen = new Set<string>();
	return entries.filter((entry) => {
		if (seen.has(entry.key)) return false;
		seen.add(entry.key);
		return true;
	});
}

/** Decide which slides the banner shows and in what order. */
export function buildFeatured(input: {
	source: FeaturedSource;
	/** Allow popular titles the library doesn't have. */
	discover: boolean;
	recent: BaseItemDto[];
	popular: PopularMatch[];
	limit?: number;
}): FeaturedEntry[] {
	const limit = input.limit ?? FEATURED_LIMIT;

	const recent: FeaturedEntry[] = input.recent.map((owned) => ({
		key: `i:${owned.Id}`,
		owned,
		popular: false,
	}));

	if (input.source === "recent") return dedupe(recent).slice(0, limit);

	const popular: FeaturedEntry[] = input.popular.flatMap(
		(match): FeaturedEntry[] => {
			if (match.owned) {
				return [
					{ key: `i:${match.owned.Id}`, owned: match.owned, popular: true },
				];
			}
			return input.discover
				? [
						{
							key: `t:${match.title.kind}:${match.title.id}`,
							discover: match.title,
						},
					]
				: [];
		},
	);

	if (input.source === "mixed") {
		return dedupe(interleave(popular, recent)).slice(0, limit);
	}

	const entries = dedupe(popular).slice(0, limit);
	if (entries.length >= MIN_POPULAR) return entries;
	// Too few popular titles to make a banner of: fill up with recently added ones.
	return dedupe([...entries, ...recent]).slice(0, limit);
}
