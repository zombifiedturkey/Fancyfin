import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import { describe, expect, it } from "vitest";
import { applyForm, splitList, toForm } from "./edit-item";

describe("splitList", () => {
	it("trims, drops blanks and removes repeats ignoring case", () => {
		expect(splitList(" Action, comedy ,, action ,Drama")).toEqual([
			"Action",
			"comedy",
			"Drama",
		]);
		expect(splitList("")).toEqual([]);
	});
});

describe("edit form", () => {
	const item: BaseItemDto = {
		Id: "1",
		Name: "Evil Dead",
		Overview: "Five friends.",
		Genres: ["Horror"],
		PremiereDate: "2013-04-05T00:00:00.0000000Z",
		CommunityRating: 6.5,
		Taglines: ["One", "Two"],
		MediaSources: [{ Id: "m" }],
	};

	it("reads an item into form fields", () => {
		const form = toForm(item);
		expect(form.premiereDate).toBe("2013-04-05");
		expect(form.communityRating).toBe("6.5");
		expect(form.genres).toBe("Horror");
		expect(form.tagline).toBe("One");
		expect(form.lock).toBe(false);
	});

	it("keeps everything it doesn't edit, including extra taglines", () => {
		const next = applyForm(item, toForm(item));
		expect(next.MediaSources).toEqual([{ Id: "m" }]);
		expect(next.Taglines).toEqual(["One", "Two"]);
		expect(next.PremiereDate).toBe("2013-04-05T00:00:00.000Z");
		expect(next.ProductionYear).toBe(2013);
	});

	it("applies changes", () => {
		const form = {
			...toForm(item),
			name: "  Evil Dead (2013) ",
			genres: "Horror, Thriller",
			premiereDate: "2014-01-02",
			communityRating: "12",
			tagline: "New",
			lock: true,
		};
		const next = applyForm(item, form);
		expect(next.Name).toBe("Evil Dead (2013)");
		expect(next.Genres).toEqual(["Horror", "Thriller"]);
		expect(next.ProductionYear).toBe(2014);
		expect(next.CommunityRating).toBe(10);
		expect(next.Taglines).toEqual(["New"]);
		expect(next.LockData).toBe(true);
	});

	it("never blanks the title", () => {
		expect(applyForm(item, { ...toForm(item), name: "  " }).Name).toBe(
			"Evil Dead",
		);
	});
});
