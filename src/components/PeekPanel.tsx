import { Link, Navigate, useSearch } from "@tanstack/react-router";
import { AnimatePresence, motion } from "motion/react";
import { useEffect } from "react";
import { hueFor } from "../lib/hue";
import { backdropImage, imageUrl, logoImage, posterImage } from "../lib/images";
import { castOf } from "../lib/item-info";
import { useItem, useSimilarItems } from "../lib/item-queries";
import { usePeek } from "../lib/peek";
import { useMotionReduced } from "../lib/settings";
import { parseTmdbPeekId } from "../lib/tmdb-detail";
import { EpisodeList } from "./EpisodeList";
import { FadeImage } from "./FadeImage";
import { ItemActions } from "./ItemActions";
import { factsFor, ItemDetails } from "./ItemDetails";
import { MediaShelf } from "./MediaShelf";
import { chip, PanelSkeleton, Score } from "./PeekParts";
import { PeopleShelf } from "./PeopleShelf";
import { TmdbPeekContent } from "./TmdbPeekContent";

function PanelContent({ itemId }: { itemId: string }) {
	const peek = usePeek();
	const item = useItem(itemId);
	const type = item.data?.Type;
	const similar = useSimilarItems(
		itemId,
		type === "Movie" || type === "Series",
	);

	if (item.isPending) return <PanelSkeleton />;
	const data = item.data;
	if (!data) {
		return (
			<p className="page-gutter pt-40 text-ink-muted">
				We couldn't find that title.
			</p>
		);
	}

	// A season is just a view of its show.
	if (data.Type === "Season" && data.SeriesId) {
		const seriesId = data.SeriesId;
		return (
			<Navigate
				to="."
				search={
					((prev: Record<string, unknown>) => ({
						...prev,
						peek: seriesId,
					})) as never
				}
				replace
			/>
		);
	}

	const isEpisode = data.Type === "Episode";
	const backdrop = backdropImage(data, 1600);
	const logo = isEpisode ? undefined : logoImage(data, 640);
	const poster =
		isEpisode && data.SeriesId && data.SeriesPrimaryImageTag
			? imageUrl(data.SeriesId, "Primary", {
					tag: data.SeriesPrimaryImageTag,
					maxWidth: 500,
				})
			: posterImage(data, 500);
	const hue = hueFor(data.Name ?? "");
	const chips = [...factsFor(data), ...(data.Genres ?? []).slice(0, 4)];
	const community = data.CommunityRating
		? `${data.CommunityRating.toFixed(1)}`
		: undefined;
	const critics = data.CriticRating
		? `${Math.round(data.CriticRating)}%`
		: undefined;

	return (
		<>
			<div className="relative">
				{backdrop && (
					<div
						aria-hidden
						className="absolute inset-x-0 top-0 h-[28rem] overflow-hidden [-webkit-mask-image:linear-gradient(to_bottom,black_35%,transparent)] [mask-image:linear-gradient(to_bottom,black_35%,transparent)]"
					>
						<FadeImage
							key={backdrop}
							src={backdrop}
							loading="eager"
							className="size-full object-cover object-top"
						/>
					</div>
				)}

				<div className="page-gutter relative pt-36 sm:pt-52">
					<div className="flex items-end gap-5 sm:gap-8">
						<div
							className="relative aspect-[2/3] w-28 shrink-0 overflow-hidden rounded-card border border-glass-border shadow-card sm:w-52"
							style={{
								backgroundImage: `linear-gradient(145deg, hsl(${hue} 45% 28%), hsl(${(hue + 50) % 360} 50% 14%))`,
							}}
						>
							{poster && (
								<FadeImage
									key={poster}
									src={poster}
									loading="eager"
									className="absolute inset-0 size-full object-cover"
								/>
							)}
						</div>

						<div className="min-w-0 pb-1">
							{isEpisode && data.SeriesId && (
								<button
									type="button"
									onClick={() => data.SeriesId && peek.open(data.SeriesId)}
									className="mb-2 cursor-pointer text-sm font-medium uppercase tracking-[0.18em] text-accent-strong hover:underline"
								>
									{data.SeriesName}
								</button>
							)}
							{logo ? (
								<FadeImage
									key={logo}
									src={logo}
									alt={data.Name ?? ""}
									loading="eager"
									className="max-h-20 w-auto max-w-full object-contain object-left sm:max-h-28"
								/>
							) : (
								<h2 className="text-3xl font-semibold leading-[1.05] tracking-tight sm:text-5xl">
									{data.Name}
								</h2>
							)}
							{data.Taglines?.[0] && (
								<p className="mt-3 text-sm italic text-ink-muted sm:text-base">
									{data.Taglines[0]}
								</p>
							)}
							{chips.length > 0 && (
								<ul className="mt-4 flex flex-wrap gap-2">
									{chips.map((text) => (
										<li key={text} className={chip}>
											{text}
										</li>
									))}
								</ul>
							)}
						</div>
					</div>

					{(community || critics) && (
						<div className="mt-7 flex flex-wrap gap-3">
							{critics && <Score label="Critics" value={critics} />}
							{community && (
								<Score label="Community" value={`★ ${community}`} />
							)}
						</div>
					)}

					{data.Overview && (
						<p className="mt-7 max-w-3xl text-base leading-relaxed text-ink/90 sm:text-lg">
							{data.Overview}
						</p>
					)}

					<ItemActions item={data} itemId={itemId} />
				</div>
			</div>

			<div className="pb-12">
				{data.Type === "Series" && (
					<EpisodeList seriesId={data.Id ?? itemId} onOpen={peek.open} />
				)}
				{isEpisode && data.SeriesId && (
					<EpisodeList
						seriesId={data.SeriesId}
						seasonId={data.SeasonId}
						currentEpisodeId={data.Id}
						onOpen={peek.open}
					/>
				)}

				<div className="mt-6">
					<PeopleShelf title="Cast" people={castOf(data)} />
					{(type === "Movie" || type === "Series") && (
						<MediaShelf
							title="More like this"
							variant="poster"
							items={similar.data}
							isLoading={similar.isPending}
							isError={similar.isError}
						/>
					)}
				</div>

				<ItemDetails item={data} />
			</div>
		</>
	);
}

function PeekPanel({ itemId }: { itemId: string }) {
	const { close } = usePeek();
	// A title the library doesn't have is shown from TMDB, in the same panel.
	const tmdb = parseTmdbPeekId(itemId);
	const reduceMotion = useMotionReduced();
	const duration = reduceMotion ? 0 : 0.28;

	// Lock the page behind the panel, and let Escape close it.
	useEffect(() => {
		const previous = document.body.style.overflow;
		document.body.style.overflow = "hidden";
		const onKeyDown = (event: KeyboardEvent) => {
			if (event.key === "Escape") close();
		};
		window.addEventListener("keydown", onKeyDown);
		return () => {
			document.body.style.overflow = previous;
			window.removeEventListener("keydown", onKeyDown);
		};
	}, [close]);

	return (
		<>
			<motion.div
				aria-hidden
				className="fixed inset-0 z-[60] bg-black/30 backdrop-blur-md"
				initial={{ opacity: 0 }}
				animate={{ opacity: 1 }}
				exit={{ opacity: 0 }}
				transition={{ duration }}
				onClick={close}
			/>

			{/* A floating glass panel with a generous margin so the page shows around it. */}
			<motion.div
				role="dialog"
				aria-modal="true"
				aria-label="Title details"
				className="fixed inset-3 z-[61] overflow-hidden rounded-[28px] border border-white/15 bg-[rgb(16_18_28/0.42)] shadow-[0_40px_120px_-20px_rgb(0_0_0/0.7),inset_0_1px_0_rgb(255_255_255/0.2)] backdrop-blur-[44px] backdrop-saturate-[1.8] sm:inset-6 lg:inset-10"
				initial={{ opacity: 0, scale: 0.96, y: 18 }}
				animate={{ opacity: 1, scale: 1, y: 0 }}
				exit={{ opacity: 0, scale: 0.97, y: 12 }}
				transition={{ duration, ease: [0.16, 1, 0.3, 1] }}
			>
				<div className="absolute right-4 top-4 z-20 flex gap-2">
					{!tmdb && (
						<Link
							to="/item/$id"
							params={{ id: itemId }}
							aria-label="Open full page"
							title="Open full page"
							className="glass flex h-10 items-center rounded-pill px-4 text-sm font-medium text-ink transition-colors hover:bg-glass-strong"
						>
							Full page ↗
						</Link>
					)}
					<button
						type="button"
						aria-label="Close"
						onClick={close}
						className="glass flex size-10 cursor-pointer items-center justify-center rounded-full text-ink transition-colors hover:bg-glass-strong"
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
							<path d="M6 6l12 12M18 6L6 18" />
						</svg>
					</button>
				</div>

				{/* Keyed so each title opens scrolled to the top. The panel's own padding
				    replaces the page gutter for everything inside (shelves included). */}
				<div
					key={itemId}
					className="h-full overflow-y-auto overscroll-contain [--page-gutter:1.25rem] sm:[--page-gutter:2.75rem]"
				>
					{tmdb ? (
						<TmdbPeekContent kind={tmdb.kind} id={tmdb.id} />
					) : (
						<PanelContent itemId={itemId} />
					)}
				</div>
			</motion.div>
		</>
	);
}

/** Shows the floating panel whenever the address has ?peek=<id>. */
export function PeekHost() {
	const search = useSearch({ strict: false }) as { peek?: unknown };
	const peek = typeof search.peek === "string" ? search.peek : undefined;

	return (
		<AnimatePresence>
			{peek && <PeekPanel key="peek" itemId={peek} />}
		</AnimatePresence>
	);
}
