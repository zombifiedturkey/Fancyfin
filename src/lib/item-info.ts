import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import type { BaseItemPerson } from "@jellyfin/sdk/lib/generated-client/models/base-item-person";

function unique<T>(values: T[]): T[] {
	return [...new Set(values)];
}

/** People credited on a title with any of the given roles (Actor, Director, Writer ...). */
export function peopleOfType(
	item: BaseItemDto,
	...types: string[]
): BaseItemPerson[] {
	return (item.People ?? []).filter(
		(person) => person.Type !== undefined && types.includes(person.Type),
	);
}

export function namesOfType(item: BaseItemDto, type: string): string[] {
	return unique(
		peopleOfType(item, type)
			.map((person) => person.Name)
			.filter((name): name is string => Boolean(name)),
	);
}

/** The cast row: actors, plus guest stars for an episode. */
export function castOf(item: BaseItemDto): BaseItemPerson[] {
	return peopleOfType(item, "Actor", "GuestStar")
		.filter((person) => person.Id && person.Name)
		.slice(0, 30);
}

/** "4K", "1080p" and so on, judged by width so wide-screen films aren't undersold. */
export function resolutionLabel(
	width: number | null | undefined,
	height: number | null | undefined,
): string | undefined {
	const w = width ?? 0;
	const h = height ?? 0;
	if (!w && !h) return undefined;
	if (w >= 3200 || h >= 2000) return "4K";
	if (w >= 1800 || h >= 1000) return "1080p";
	if (w >= 1200 || h >= 700) return "720p";
	return h ? `${h}p` : undefined;
}

export function formatBytes(bytes: number): string {
	const gb = bytes / 1024 ** 3;
	if (gb >= 1) return `${gb.toFixed(1)} GB`;
	return `${Math.max(1, Math.round(bytes / 1024 ** 2))} MB`;
}

/** "eng" -> "English", falling back to the code itself. */
function languageName(code: string): string {
	try {
		return new Intl.DisplayNames(["en"], { type: "language" }).of(code) ?? code;
	} catch {
		return code;
	}
}

function summarise(values: string[], max: number): string {
	const shown = values.slice(0, max).join(", ");
	return values.length > max ? `${shown} +${values.length - max} more` : shown;
}

/** Technical facts about the file, for the details section. */
export function mediaFacts(
	item: BaseItemDto,
): { label: string; value: string }[] {
	const source = item.MediaSources?.[0];
	const streams = source?.MediaStreams ?? item.MediaStreams ?? [];
	const facts: { label: string; value: string }[] = [];

	const video = streams.find((stream) => stream.Type === "Video");
	if (video) {
		const range =
			video.VideoRangeType &&
			video.VideoRangeType !== "SDR" &&
			video.VideoRangeType !== "Unknown"
				? video.VideoRangeType
				: undefined;
		const value = [
			resolutionLabel(video.Width, video.Height),
			video.Codec?.toUpperCase(),
			range,
		]
			.filter(Boolean)
			.join(" · ");
		if (value) facts.push({ label: "Video", value });
	}

	const audio = unique(
		streams
			.filter((stream) => stream.Type === "Audio")
			.map((stream) => stream.DisplayTitle ?? stream.Language)
			.filter((value): value is string => Boolean(value)),
	);
	if (audio.length > 0)
		facts.push({ label: "Audio", value: summarise(audio, 3) });

	const subtitles = unique(
		streams
			.filter((stream) => stream.Type === "Subtitle")
			.map((stream) => stream.Language)
			.filter((value): value is string => Boolean(value))
			.map(languageName),
	);
	if (subtitles.length > 0) {
		facts.push({ label: "Subtitles", value: summarise(subtitles, 6) });
	}

	if (source?.Size)
		facts.push({ label: "Size", value: formatBytes(source.Size) });
	return facts;
}
