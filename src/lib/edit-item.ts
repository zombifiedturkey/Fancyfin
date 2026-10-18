import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";

/** The details a person can change, as plain strings ready for form inputs. */
export type EditForm = {
	name: string;
	originalTitle: string;
	sortName: string;
	/** yyyy-mm-dd, or empty. */
	premiereDate: string;
	officialRating: string;
	communityRating: string;
	tagline: string;
	overview: string;
	/** Comma separated. */
	genres: string;
	/** Comma separated. */
	tags: string;
	lock: boolean;
};

const DATE_PREFIX = /^(\d{4}-\d{2}-\d{2})/;

/** "Action, comedy ,Action" becomes ["Action", "comedy"]. Blank entries and repeats are dropped. */
export function splitList(text: string): string[] {
	const seen = new Set<string>();
	const out: string[] = [];
	for (const part of text.split(",")) {
		const value = part.trim();
		const key = value.toLowerCase();
		if (!value || seen.has(key)) continue;
		seen.add(key);
		out.push(value);
	}
	return out;
}

export function toForm(item: BaseItemDto): EditForm {
	return {
		name: item.Name ?? "",
		originalTitle: item.OriginalTitle ?? "",
		sortName: item.ForcedSortName ?? "",
		premiereDate: DATE_PREFIX.exec(item.PremiereDate ?? "")?.[1] ?? "",
		officialRating: item.OfficialRating ?? "",
		communityRating:
			item.CommunityRating === undefined || item.CommunityRating === null
				? ""
				: String(item.CommunityRating),
		tagline: item.Taglines?.[0] ?? "",
		overview: item.Overview ?? "",
		genres: (item.Genres ?? []).join(", "),
		tags: (item.Tags ?? []).join(", "),
		lock: item.LockData ?? false,
	};
}

/** The item as the server should now store it. Everything the form doesn't cover is passed through untouched. */
export function applyForm(item: BaseItemDto, form: EditForm): BaseItemDto {
	const next: BaseItemDto = { ...item };
	next.Name = form.name.trim() || item.Name;
	next.OriginalTitle = form.originalTitle.trim();
	next.ForcedSortName = form.sortName.trim();
	next.OfficialRating = form.officialRating.trim() || undefined;
	next.Overview = form.overview.trim();
	next.Genres = splitList(form.genres);
	next.Tags = splitList(form.tags);
	next.LockData = form.lock;

	const date = DATE_PREFIX.exec(form.premiereDate)?.[1];
	if (date) {
		next.PremiereDate = `${date}T00:00:00.000Z`;
		next.ProductionYear = Number(date.slice(0, 4));
	} else {
		next.PremiereDate = undefined;
	}

	const rating = Number.parseFloat(form.communityRating);
	next.CommunityRating = Number.isNaN(rating)
		? undefined
		: Math.min(10, Math.max(0, rating));

	// Only touch the taglines if the box changed. An item may carry several and we show one.
	const originalTagline = item.Taglines?.[0] ?? "";
	if (form.tagline.trim() !== originalTagline) {
		next.Taglines = form.tagline.trim() ? [form.tagline.trim()] : [];
	}
	return next;
}
