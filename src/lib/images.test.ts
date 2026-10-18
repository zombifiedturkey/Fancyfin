import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import { describe, expect, it } from "vitest";
import {
	backdropImage,
	imageUrl,
	landscapeImage,
	logoImage,
	posterImage,
} from "./images";

describe("imageUrl", () => {
	it("builds an image URL with tag and size", () => {
		expect(imageUrl("abc", "Primary", { tag: "t1", maxWidth: 400 })).toBe(
			"/api/Items/abc/Images/Primary?maxWidth=400&quality=90&tag=t1",
		);
	});

	it("includes the image index when given", () => {
		expect(
			imageUrl("abc", "Backdrop", { tag: "b", maxWidth: 1920, index: 0 }),
		).toBe("/api/Items/abc/Images/Backdrop/0?maxWidth=1920&quality=90&tag=b");
	});

	it("leaves the tag out when there is none", () => {
		expect(imageUrl("abc", "Logo", { maxWidth: 300 })).toBe(
			"/api/Items/abc/Images/Logo?maxWidth=300&quality=90",
		);
	});
});

describe("posterImage", () => {
	it("uses the Primary image", () => {
		const item: BaseItemDto = { Id: "m1", ImageTags: { Primary: "p" } };
		expect(posterImage(item, 400)).toContain("/Items/m1/Images/Primary");
	});

	it("returns undefined when the item has no poster", () => {
		expect(posterImage({ Id: "m1" }, 400)).toBeUndefined();
	});
});

describe("backdropImage and logoImage", () => {
	it("falls back to the parent's backdrop for episodes", () => {
		const episode: BaseItemDto = {
			Id: "e1",
			ParentBackdropItemId: "s1",
			ParentBackdropImageTags: ["bd"],
		};
		expect(backdropImage(episode, 1920)).toContain(
			"/Items/s1/Images/Backdrop/0",
		);
	});

	it("falls back to the parent's logo", () => {
		const episode: BaseItemDto = {
			Id: "e1",
			ParentLogoItemId: "s1",
			ParentLogoImageTag: "lg",
		};
		expect(logoImage(episode, 400)).toContain("/Items/s1/Images/Logo");
	});
});

describe("landscapeImage", () => {
	it("uses an episode's own still first", () => {
		const episode: BaseItemDto = {
			Id: "e1",
			Type: "Episode",
			ImageTags: { Primary: "still" },
		};
		expect(landscapeImage(episode, 640)).toContain("/Items/e1/Images/Primary");
	});

	it("uses the series thumb when an episode has no still", () => {
		const episode: BaseItemDto = {
			Id: "e1",
			Type: "Episode",
			ParentThumbItemId: "s1",
			ParentThumbImageTag: "th",
		};
		expect(landscapeImage(episode, 640)).toContain("/Items/s1/Images/Thumb");
	});

	it("prefers a movie's thumb, then its backdrop, then its poster", () => {
		const withThumb: BaseItemDto = {
			Id: "m1",
			Type: "Movie",
			ImageTags: { Thumb: "t", Primary: "p" },
			BackdropImageTags: ["b"],
		};
		expect(landscapeImage(withThumb, 640)).toContain("/Images/Thumb");

		const withBackdrop: BaseItemDto = {
			Id: "m1",
			Type: "Movie",
			ImageTags: { Primary: "p" },
			BackdropImageTags: ["b"],
		};
		expect(landscapeImage(withBackdrop, 640)).toContain("/Images/Backdrop/0");

		const posterOnly: BaseItemDto = {
			Id: "m1",
			Type: "Movie",
			ImageTags: { Primary: "p" },
		};
		expect(landscapeImage(posterOnly, 640)).toContain("/Images/Primary");
	});
});
