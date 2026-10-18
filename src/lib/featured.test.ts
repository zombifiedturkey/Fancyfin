import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import { describe, expect, it } from "vitest";
import { buildFeatured, interleave, type PopularMatch } from "./featured";
import type { TmdbTitle } from "./tmdb";

const owned = (id: string): BaseItemDto => ({ Id: id, Name: id });
const tmdb = (id: number, kind: "movie" | "tv" = "movie"): TmdbTitle => ({
	id,
	kind,
	title: `t${id}`,
	overview: "x",
	backdrop: "/a",
	popularity: 1,
});
const keys = (entries: { key: string }[]) => entries.map((e) => e.key);

describe("interleave", () => {
	it("alternates and keeps the leftovers", () => {
		expect(interleave<number | string>([1, 2, 3], ["a"])).toEqual([
			1,
			"a",
			2,
			3,
		]);
		expect(interleave([], [])).toEqual([]);
	});
});

describe("buildFeatured", () => {
	const recent = [owned("r1"), owned("r2"), owned("r3")];
	const popular: PopularMatch[] = [
		{ title: tmdb(1), owned: owned("p1") },
		{ title: tmdb(2) },
		{ title: tmdb(3, "tv") },
		{ title: tmdb(4), owned: owned("p4") },
	];

	it("recent source ignores popular titles", () => {
		expect(
			keys(
				buildFeatured({ source: "recent", discover: true, recent, popular }),
			),
		).toEqual(["i:r1", "i:r2", "i:r3"]);
	});

	it("popular source keeps order and includes titles not owned when allowed", () => {
		expect(
			keys(
				buildFeatured({ source: "popular", discover: true, recent, popular }),
			),
		).toEqual(["i:p1", "t:movie:2", "t:tv:3", "i:p4"]);
	});

	it("leaves out titles not owned when discovery is off, topping up from recent", () => {
		const result = buildFeatured({
			source: "popular",
			discover: false,
			recent,
			popular,
		});
		expect(keys(result)).toEqual(["i:p1", "i:p4", "i:r1", "i:r2", "i:r3"]);
		expect(result.every((entry) => "owned" in entry)).toBe(true);
	});

	it("marks library titles that came from the popular list", () => {
		const [first] = buildFeatured({
			source: "popular",
			discover: true,
			recent,
			popular,
		});
		expect(first && "owned" in first && first.popular).toBe(true);
	});

	it("mixed alternates popular and recent without repeats", () => {
		const result = buildFeatured({
			source: "mixed",
			discover: false,
			recent: [owned("p1"), owned("r2")],
			popular,
		});
		expect(keys(result)).toEqual(["i:p1", "i:p4", "i:r2"]);
	});

	it("respects the limit", () => {
		expect(
			buildFeatured({
				source: "popular",
				discover: true,
				recent,
				popular,
				limit: 2,
			}),
		).toHaveLength(2);
	});
});
