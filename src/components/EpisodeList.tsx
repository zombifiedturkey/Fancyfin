import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import { Link } from "@tanstack/react-router";
import { type ReactNode, useEffect, useRef, useState } from "react";
import { formatDate } from "../lib/dates";
import { clampPercent, formatRuntime, ticksToMinutes } from "../lib/format";
import { hueFor } from "../lib/hue";
import { landscapeImage } from "../lib/images";
import { useEpisodes, useSeasons } from "../lib/item-queries";
import { useSetSeasonPlayed } from "../lib/season-queries";
import {
	defaultSeason,
	nextEpisodeId,
	orderSeasons,
	seasonCounts,
	watchedCount,
} from "../lib/seasons";
import { updateSettings, useSettings } from "../lib/settings";
import { FadeImage } from "./FadeImage";
import { cardWidth } from "./MediaCard";
import { Shelf } from "./Shelf";

const TICKS_PER_SECOND = 10_000_000;

/** Opens an episode in the floating panel when `onOpen` is given, otherwise on its own page. */
function Open({
	episode,
	onOpen,
	className,
	children,
}: {
	episode: BaseItemDto;
	onOpen?: (id: string) => void;
	className: string;
	children: ReactNode;
}) {
	const id = episode.Id;
	if (!id) return null;
	return onOpen ? (
		<button
			type="button"
			onClick={() => onOpen(id)}
			className={`${className} cursor-pointer text-left`}
		>
			{children}
		</button>
	) : (
		<Link to="/item/$id" params={{ id }} className={className}>
			{children}
		</Link>
	);
}

function UpNext() {
	return (
		<span className="rounded-pill bg-accent/20 px-2 py-0.5 text-xs font-semibold text-accent-strong">
			Up next
		</span>
	);
}

/** The episode's picture with its watched tick or progress bar on top. */
function Thumbnail({
	episode,
	highlight,
	className,
}: {
	episode: BaseItemDto;
	highlight: boolean;
	className: string;
}) {
	const title = episode.Name ?? "Episode";
	const hue = hueFor(title);
	const src = landscapeImage(episode, 480);
	const progress = clampPercent(episode.UserData?.PlayedPercentage);
	const played = episode.UserData?.Played ?? false;

	return (
		<div
			className={`relative aspect-video overflow-hidden rounded-card border shadow-card ${className} ${
				highlight ? "border-accent ring-2 ring-accent" : "border-glass-border"
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
					<div className="h-full bg-accent" style={{ width: `${progress}%` }} />
				</div>
			)}
		</div>
	);
}

function numberPrefix(episode: BaseItemDto) {
	return episode.IndexNumber !== undefined && episode.IndexNumber !== null ? (
		<span className="text-ink-muted">{episode.IndexNumber}. </span>
	) : null;
}

/** Compact card for the horizontal row layout. */
function EpisodeCard({
	episode,
	current,
	next,
	onOpen,
}: {
	episode: BaseItemDto;
	current: boolean;
	next: boolean;
	onOpen?: (id: string) => void;
}) {
	const runtime = formatRuntime(ticksToMinutes(episode.RunTimeTicks));
	return (
		<Open
			episode={episode}
			onOpen={onOpen}
			className="group/episode block w-full transition-transform duration-200 hover:-translate-y-1.5 hover:scale-[1.04] focus-visible:-translate-y-1.5 focus-visible:scale-[1.04]"
		>
			<Thumbnail episode={episode} highlight={current} className="" />
			<p className="mt-3 truncate text-sm font-medium">
				{numberPrefix(episode)}
				{episode.Name ?? "Episode"}
			</p>
			<p className="mt-0.5 flex items-center gap-2 text-xs text-ink-muted">
				{runtime}
				{next && <UpNext />}
			</p>
		</Open>
	);
}

/** Wide row for the list layout: a big picture, then title, facts and the description. */
function EpisodeRow({
	episode,
	current,
	next,
	onOpen,
}: {
	episode: BaseItemDto;
	current: boolean;
	next: boolean;
	onOpen?: (id: string) => void;
}) {
	const runtime = formatRuntime(ticksToMinutes(episode.RunTimeTicks));
	const aired = formatDate(episode.PremiereDate);
	const facts = [runtime, aired].filter(Boolean).join("  ·  ");
	const resumeAt = Math.floor(
		(episode.UserData?.PlaybackPositionTicks ?? 0) / TICKS_PER_SECOND,
	);

	return (
		<li
			className={`flex items-center gap-2 rounded-card transition-colors hover:bg-glass ${
				current ? "bg-glass" : ""
			}`}
		>
			<Open
				episode={episode}
				onOpen={onOpen}
				className="flex min-w-0 flex-1 gap-4 rounded-card p-3 outline-offset-2"
			>
				<Thumbnail
					episode={episode}
					highlight={current || next}
					className="w-40 shrink-0 sm:w-60"
				/>
				<span className="min-w-0 flex-1">
					<span className="block truncate text-base font-medium">
						{numberPrefix(episode)}
						{episode.Name ?? "Episode"}
					</span>
					<span className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-ink-muted">
						{facts}
						{next && <UpNext />}
					</span>
					{episode.Overview && (
						<span className="mt-2 line-clamp-2 block text-sm text-ink-muted sm:line-clamp-3">
							{episode.Overview}
						</span>
					)}
				</span>
			</Open>
			{episode.Id && (
				<Link
					to="/watch/$id"
					params={{ id: episode.Id }}
					search={{ start: resumeAt }}
					aria-label={`${resumeAt > 0 ? "Resume" : "Play"} ${episode.Name ?? "episode"}`}
					className="glass mr-3 flex size-11 shrink-0 items-center justify-center rounded-full text-ink transition-colors hover:bg-glass-strong"
				>
					<svg
						aria-hidden
						viewBox="0 0 24 24"
						className="size-4"
						fill="currentColor"
					>
						<path d="M7 4.5v15l13-7.5z" />
					</svg>
				</Link>
			)}
		</li>
	);
}

function LayoutToggle() {
	const { seasonLayout } = useSettings();
	const button = (active: boolean) =>
		`flex size-8 cursor-pointer items-center justify-center rounded-full transition-colors ${
			active ? "bg-ink text-canvas" : "text-ink-muted hover:text-ink"
		}`;
	return (
		<fieldset className="glass flex min-w-0 items-center gap-1 rounded-pill p-1">
			<legend className="sr-only">Episode layout</legend>
			<button
				type="button"
				aria-pressed={seasonLayout === "list"}
				aria-label="List"
				title="List"
				onClick={() => updateSettings({ seasonLayout: "list" })}
				className={button(seasonLayout === "list")}
			>
				<svg
					aria-hidden
					viewBox="0 0 24 24"
					className="size-4"
					fill="none"
					stroke="currentColor"
					strokeWidth="2.5"
					strokeLinecap="round"
				>
					<path d="M4 6h16M4 12h16M4 18h16" />
				</svg>
			</button>
			<button
				type="button"
				aria-pressed={seasonLayout === "row"}
				aria-label="Row of cards"
				title="Row of cards"
				onClick={() => updateSettings({ seasonLayout: "row" })}
				className={button(seasonLayout === "row")}
			>
				<svg
					aria-hidden
					viewBox="0 0 24 24"
					className="size-4"
					fill="none"
					stroke="currentColor"
					strokeWidth="2.5"
					strokeLinejoin="round"
				>
					<rect x="3" y="6" width="7" height="12" rx="1.5" />
					<rect x="14" y="6" width="7" height="12" rx="1.5" />
				</svg>
			</button>
		</fieldset>
	);
}

/**
 * A series' seasons and episodes: season pills with watched counts, a summary of the chosen season
 * (with mark watched), and the episodes as a list with descriptions or a row of cards.
 */
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
	const { seasonLayout } = useSettings();
	const seasons = useSeasons(seriesId);
	const [picked, setPicked] = useState<string | undefined>(
		initialSeasonId ?? undefined,
	);

	const seasonList = orderSeasons(seasons.data ?? []);
	const seasonId = picked ?? defaultSeason(seasonList)?.Id;
	const episodes = useEpisodes(seriesId, seasonId);
	const setSeasonPlayed = useSetSeasonPlayed(seasonId);

	const list = episodes.data ?? [];
	const watched = watchedCount(list);
	const allWatched = list.length > 0 && watched === list.length;
	// "Up next" only makes sense when you're browsing the show, not viewing one episode.
	const nextId = currentEpisodeId ? undefined : nextEpisodeId(list);

	// In the row layout, bring the episode being viewed to the middle of the row.
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

	const pills =
		seasonList.length > 1 ? (
			<div className="flex flex-wrap gap-2">
				{seasonList.map((season) => {
					const counts = seasonCounts(season);
					const active = season.Id === seasonId;
					return (
						<button
							key={season.Id}
							type="button"
							aria-pressed={active}
							onClick={() => setPicked(season.Id)}
							className={`flex cursor-pointer items-center gap-2 rounded-pill px-4 py-2 text-sm font-medium transition-colors ${
								active
									? "bg-ink text-canvas"
									: "glass text-ink-muted hover:text-ink"
							}`}
						>
							{season.Name ?? `Season ${season.IndexNumber ?? ""}`}
							{counts && (
								<span
									className={`text-xs ${active ? "opacity-60" : "opacity-70"}`}
								>
									{counts.watched === counts.total
										? "✓"
										: `${counts.watched}/${counts.total}`}
								</span>
							)}
						</button>
					);
				})}
			</div>
		) : null;

	const summary =
		list.length > 0 ? (
			<div className="flex flex-wrap items-center gap-x-5 gap-y-3 text-sm">
				<span className="flex items-center gap-3 text-ink-muted">
					<span
						role="progressbar"
						aria-label="Watched in this season"
						aria-valuemin={0}
						aria-valuemax={list.length}
						aria-valuenow={watched}
						className="h-1.5 w-28 overflow-hidden rounded-pill bg-glass-strong"
					>
						<span
							className="block h-full bg-accent"
							style={{ width: `${(watched / list.length) * 100}%` }}
						/>
					</span>
					{watched} of {list.length} watched
				</span>
				<button
					type="button"
					disabled={setSeasonPlayed.isPending}
					onClick={() => setSeasonPlayed.mutate(!allWatched)}
					className="cursor-pointer font-medium text-ink-muted transition-colors hover:text-ink disabled:cursor-wait disabled:opacity-60"
				>
					{allWatched ? "Mark season unwatched" : "Mark season watched"}
				</button>
				<LayoutToggle />
			</div>
		) : (
			<LayoutToggle />
		);

	const toolbar = (
		<div className="flex flex-col items-start gap-4">
			{pills}
			{summary}
		</div>
	);

	const status = episodes.isPending ? (
		<p className="text-sm text-ink-muted">Loading episodes…</p>
	) : episodes.isError ? (
		<p className="text-sm text-ink-muted">Couldn't load the episodes.</p>
	) : list.length === 0 ? (
		<p className="text-sm text-ink-muted">No episodes in this season.</p>
	) : null;

	if (seasonLayout === "list") {
		return (
			<section aria-label="Episodes" className="content-column mt-10">
				<h2 className="text-xl font-semibold tracking-tight">Episodes</h2>
				<div className="mt-4">{toolbar}</div>
				<div className="mt-5">
					{episodes.isPending ? (
						<ul className="space-y-3" aria-hidden>
							{Array.from({ length: 4 }, (_, i) => (
								<li key={i} className="shimmer h-28 rounded-card" />
							))}
						</ul>
					) : (
						(status ?? (
							<ul className="-mx-3 space-y-1">
								{list.map((episode) => (
									<EpisodeRow
										key={episode.Id}
										episode={episode}
										current={episode.Id === currentEpisodeId}
										next={episode.Id === nextId}
										onOpen={onOpen}
									/>
								))}
							</ul>
						))
					)}
				</div>
			</section>
		);
	}

	const width = `${cardWidth.landscape} shrink-0`;
	return (
		<div className="mt-10">
			<Shelf title="Episodes" toolbar={toolbar}>
				{episodes.isPending ? (
					Array.from({ length: 6 }, (_, i) => (
						<li key={i} className={width}>
							<div className="shimmer aspect-video rounded-card border border-glass-border" />
						</li>
					))
				) : status ? (
					<li>{status}</li>
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
								next={episode.Id === nextId}
								onOpen={onOpen}
							/>
						</li>
					))
				)}
			</Shelf>
		</div>
	);
}
