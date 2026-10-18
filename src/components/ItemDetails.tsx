import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import { formatDate } from "../lib/dates";
import { episodeLabel, formatRuntime, ticksToMinutes } from "../lib/format";
import { mediaFacts, namesOfType } from "../lib/item-info";

/** The short facts about a title: episode number, year, rating, length. */
export function factsFor(item: BaseItemDto): string[] {
	const parts: string[] = [];
	if (item.Type === "Episode") {
		const label = episodeLabel(item);
		if (label) parts.push(label);
		const aired = formatDate(item.PremiereDate);
		if (aired) parts.push(aired);
	} else if (item.ProductionYear) {
		parts.push(String(item.ProductionYear));
	}
	if (item.OfficialRating) parts.push(item.OfficialRating);
	if (item.Type === "Series") {
		const seasons = item.ChildCount;
		if (seasons) parts.push(seasons === 1 ? "1 season" : `${seasons} seasons`);
	} else {
		const runtime = formatRuntime(ticksToMinutes(item.RunTimeTicks));
		if (runtime) parts.push(runtime);
	}
	return parts;
}

/** Director, writers, studio, genres and file info. */
export function ItemDetails({ item }: { item: BaseItemDto }) {
	const entries: { label: string; value: string }[] = [];
	const add = (label: string, values: string[]) => {
		if (values.length > 0) entries.push({ label, value: values.join(", ") });
	};
	add("Director", namesOfType(item, "Director"));
	add("Writers", namesOfType(item, "Writer"));
	add(
		"Studio",
		(item.Studios ?? [])
			.map((studio) => studio.Name)
			.filter((name): name is string => Boolean(name)),
	);
	add("Genres", item.Genres ?? []);
	entries.push(...mediaFacts(item));

	if (entries.length === 0) return null;
	return (
		<section className="content-column mt-10" aria-label="Details">
			<h2 className="text-xl font-semibold tracking-tight">Details</h2>
			<dl className="mt-5 grid max-w-4xl gap-x-8 gap-y-5 sm:grid-cols-2">
				{entries.map((entry) => (
					<div key={entry.label}>
						<dt className="text-xs font-medium uppercase tracking-wider text-ink-faint">
							{entry.label}
						</dt>
						<dd className="mt-1 text-sm">{entry.value}</dd>
					</div>
				))}
			</dl>
		</section>
	);
}
