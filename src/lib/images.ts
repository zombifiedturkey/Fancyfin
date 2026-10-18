import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import { apiUrl } from "./api-base";

type ImageKind = "Primary" | "Backdrop" | "Thumb" | "Logo";

type ImageOptions = {
	/** The image's cache tag from the item. Lets the browser cache it until the picture changes. */
	tag?: string | null;
	maxWidth: number;
	quality?: number;
	/** Which of several images (backdrops can have more than one). */
	index?: number;
};

/** Same-origin URL for an item's image. Image requests are anonymous, so plain <img> tags work. */
export function imageUrl(
	itemId: string,
	kind: ImageKind,
	{ tag, maxWidth, quality = 90, index }: ImageOptions,
): string {
	const params = new URLSearchParams({
		maxWidth: String(maxWidth),
		quality: String(quality),
	});
	if (tag) params.set("tag", tag);
	const path =
		index === undefined
			? `/Items/${itemId}/Images/${kind}`
			: `/Items/${itemId}/Images/${kind}/${index}`;
	return apiUrl(`${path}?${params.toString()}`);
}

/** Portrait poster (2:3). Movies and series. */
export function posterImage(
	item: BaseItemDto,
	maxWidth: number,
): string | undefined {
	const tag = item.ImageTags?.Primary;
	return item.Id && tag
		? imageUrl(item.Id, "Primary", { tag, maxWidth })
		: undefined;
}

/** Wide backdrop (16:9). Falls back to the parent's (the series') backdrop for episodes. */
export function backdropImage(
	item: BaseItemDto,
	maxWidth: number,
): string | undefined {
	const tag = item.BackdropImageTags?.[0];
	if (item.Id && tag)
		return imageUrl(item.Id, "Backdrop", { tag, maxWidth, index: 0 });

	const parentTag = item.ParentBackdropImageTags?.[0];
	if (item.ParentBackdropItemId && parentTag) {
		return imageUrl(item.ParentBackdropItemId, "Backdrop", {
			tag: parentTag,
			maxWidth,
			index: 0,
		});
	}
	return undefined;
}

/** Title logo with a transparent background, for the hero. */
export function logoImage(
	item: BaseItemDto,
	maxWidth: number,
): string | undefined {
	const tag = item.ImageTags?.Logo;
	if (item.Id && tag) return imageUrl(item.Id, "Logo", { tag, maxWidth });

	if (item.ParentLogoItemId && item.ParentLogoImageTag) {
		return imageUrl(item.ParentLogoItemId, "Logo", {
			tag: item.ParentLogoImageTag,
			maxWidth,
		});
	}
	return undefined;
}

/** Wide card image (16:9) for "Continue Watching" and "Next Up". */
export function landscapeImage(
	item: BaseItemDto,
	maxWidth: number,
): string | undefined {
	if (item.Type === "Episode") {
		// An episode's own picture is a still from the episode, which is already 16:9.
		const still = item.ImageTags?.Primary;
		if (item.Id && still)
			return imageUrl(item.Id, "Primary", { tag: still, maxWidth });

		if (item.ParentThumbItemId && item.ParentThumbImageTag) {
			return imageUrl(item.ParentThumbItemId, "Thumb", {
				tag: item.ParentThumbImageTag,
				maxWidth,
			});
		}
		return backdropImage(item, maxWidth);
	}

	const thumb = item.ImageTags?.Thumb;
	if (item.Id && thumb)
		return imageUrl(item.Id, "Thumb", { tag: thumb, maxWidth });

	return backdropImage(item, maxWidth) ?? posterImage(item, maxWidth);
}
