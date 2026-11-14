import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import { describe, expect, it } from "vitest";
import { attentionReasons } from "./attention";

const fine: BaseItemDto = {
	Id: "1",
	Name: "Heat",
	ProviderIds: { Tmdb: "949" },
	ImageTags: { Primary: "abc" },
	Overview: "A crew of thieves.",
};

describe("attentionReasons", () => {
	it("is empty for a title with an id, a poster and a description", () => {
		expect(attentionReasons(fine)).toEqual([]);
	});

	it("flags a title that was never matched", () => {
		expect(attentionReasons({ ...fine, ProviderIds: {} })).toEqual(["match"]);
		expect(attentionReasons({ ...fine, ProviderIds: { Tmdb: " " } })).toEqual([
			"match",
		]);
		expect(attentionReasons({ ...fine, ProviderIds: undefined })).toEqual([
			"match",
		]);
	});

	it("accepts any one of the known ids", () => {
		expect(
			attentionReasons({ ...fine, ProviderIds: { Imdb: "tt0113277" } }),
		).toEqual([]);
		expect(
			attentionReasons({ ...fine, ProviderIds: { Tvdb: "81189" } }),
		).toEqual([]);
	});

	it("flags a missing poster and a missing description", () => {
		expect(attentionReasons({ ...fine, ImageTags: {} })).toEqual(["poster"]);
		expect(attentionReasons({ ...fine, Overview: "  " })).toEqual([
			"description",
		]);
	});

	it("lists everything that is missing, in order", () => {
		expect(attentionReasons({ Id: "2", Name: "Mystery" })).toEqual([
			"match",
			"poster",
			"description",
		]);
	});
});
