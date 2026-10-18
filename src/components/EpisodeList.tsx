import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import { Link } from "@tanstack/react-router";
import { useEffect, useRef, useState } from "react";
import { clampPercent } from "../lib/format";
import { hueFor } from "../lib/hue";
import { landscapeImage } from "../lib/images";
import { useEpisodes, useSeasons } from "../lib/item-queries";
import { FadeImage } from "./FadeImage";
import { cardWidth } from "./MediaCard";
import { Shelf } from "./Shelf";

/** The first real season with something left to watch, else the first real season. */
function defaultSeason(seasons: BaseItemDto[]): BaseItemDto | undefined {
	const real = seasons.filter((season) => season.IndexNumber !== 0);
	return (
		real.find((season) => (season.UserData?.UnplayedItemCount ?? 0) > 0) ??
		real[0] ??
		seasons[0]
	);
}

function EpisodeCard({
	episode,
	current,
	onOpen,
}: {
	episode: BaseItemDto;
	current: boolean;
	onOpen?: (id: string) => void;
}) {
	const title = episode.Name ?? "Episode";
	const hue = hueFor(title);
	const src = landscapeImage(episode, 480);
	const progress = clampPercent(episode.UserData?.PlayedPercentage);
	const played = episode.UserData?.Played ?? false;

	if (!episode.Id) return null;

	const className =
		"group/episode block transition-transform duration-200 hover:-translate-y-1.5 hover:scale-[1.04] focus-visible:-translate-y-1.5 focus-visible:scale-[1.04]";
	const body = (
		<>
			<div
				className={`relative aspect-video overflow-hidden rounded-card border shadow-card ${
					current ? "border-accent ring-2 ring-accent" : "border-glass-border"
				}`}
				style={{
					backgroundImage: `linear-gradient(145deg, hsl(${hue} 45% 28%), hsl(${(hue + 50) % 360} 50% 14%))`,
				}}
			>
				{src && (
					<FadeImage
						key={src}
						src={src}
						className="absolute inset-0 size-full object-cover"
					/>
				)}
				{played && (
					<span
						title="Watched"
						className="glass absolute right-2 top-2 flex size-6 items-center justify-center rounded-full text-xs"
					>
						✓
					</span>
				)}
				{progress > 0 && !played && (
					<div className="absolute inset-x-0 bottom-0 h-1 bg-black/50">
						<div
							className="h-full bg-accent"
							style={{ width: `${progress}%` }}
						/>
					</div>
				)}
			</div>
			<p className="mt-3 truncate text-sm font-medium">
				{episode.IndexNumber !== undefined && episode.IndexNumber !== null && (
					<span className="text-ink-muted">{episode.IndexNumber}. </span>
				)}
				{title}
			</p>
		</>
	);

	return onOpen ? (
		<button
			type="button"
			onClick={() => onOpen(episode.Id as string)}
			className={`${className} w-full cursor-pointer text-left`}
		>
			{body}
		</button>
	) : (
		<Link
			to="/item/$id"
			params={{ id: episode.Id as string }}
			className={className}
		>
			{body}
		</Link>
	);
}

/** Season tabs and a scrolling row of the chosen season's episodes. */
export function EpisodeList({
	seriesId,
	seasonId: initialSeasonId,
	currentEpisodeId,
	onOpen,
}: {
	seriesId: string;
	/** Start on this season (used when viewing an episode). */
	seasonId?: string | null;
	currentEpisodeId?: string;
	/** Open an episode in the floating panel instead of its own page. */
	onOpen?: (id: string) => void;
}) {
	const seasons = useSeasons(seriesId);
	const [picked, setPicked] = useState<string | undefined>(
		initialSeasonId ?? undefined,
	);

	const seasonList = seasons.data ?? [];
	const seasonId = picked ?? defaultSeason(seasonList)?.Id;
	const episodes = useEpisodes(seriesId, seasonId);

	// When viewing an episode, bring it to the middle of the row.
	const currentRef = useRef<HTMLLIElement>(null);
	useEffect(() => {
		const element = currentRef.current;
		const scroller = element?.closest<HTMLElement>(".overflow-x-auto");
		if (!episodes.data || !element || !scroller) return;
		const delta =
			element.getBoundingClientRect().left -
			scroller.getBoundingClientRect().left -
			(scroller.clientWidth - element.clientWidth) / 2;
		scroller.scrollLeft += delta;
	}, [episodes.data]);

	if (seasons.isPending) {
		return (
			<section className="content-column mt-10" aria-hidden>
				<div className="shimmer h-7 w-40 rounded-pill" />
				<div className="shimmer mt-6 aspect-video w-72 rounded-card" />
			</section>
		);
	}
	if (seasonList.length === 0) return null;

	const tabs =
		seasonList.length > 1 ? (
			<div className="flex flex-wrap gap-2">
				{seasonList.map((season) => (
					<button
						key={season.Id}
						type="button"
						aria-pressed={season.Id === seasonId}
						onClick={() => setPicked(season.Id)}
						className={`cursor-pointer rounded-pill px-4 py-2 text-sm font-medium transition-colors ${
							season.Id === seasonId
								? "bg-ink text-canvas"
								: "glass text-ink-muted hover:text-ink"
						}`}
					>
						{season.Name ?? `Season ${season.IndexNumber ?? ""}`}
					</button>
				))}
			</div>
		) : undefined;

	const width = `${cardWidth.landscape} shrink-0`;
	const list = episodes.data ?? [];

	return (
		<div className="mt-10">
			<Shelf title="Episodes" toolbar={tabs}>
				{episodes.isPending ? (
					Array.from({ length: 6 }, (_, i) => (
						<li key={i} className={width}>
							<div className="shimmer aspect-video rounded-card border border-glass-border" />
						</li>
					))
				) : episodes.isError ? (
					<li className="text-sm text-ink-muted">
						Couldn't load the episodes.
					</li>
				) : list.length === 0 ? (
					<li className="text-sm text-ink-muted">
						No episodes in this season.
					</li>
				) : (
					list.map((episode) => (
						<li
							key={episode.Id}
							ref={episode.Id === currentEpisodeId ? currentRef : undefined}
							className={width}
						>
							<EpisodeCard
								episode={episode}
								current={episode.Id === currentEpisodeId}
								onOpen={onOpen}
							/>
						</li>
					))
				)}
			</Shelf>
		</div>
	);
}
