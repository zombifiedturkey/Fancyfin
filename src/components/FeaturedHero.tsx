import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import { AnimatePresence, motion } from "motion/react";
import { useEffect, useState } from "react";
import { arrForKind, useArrAvailability } from "../lib/arr-queries";
import type { FeaturedEntry } from "../lib/featured";
import { formatRuntime, ticksToMinutes } from "../lib/format";
import { backdropImage, logoImage } from "../lib/images";
import { usePeek } from "../lib/peek";
import { updateSettings, useMotionReduced, useSettings } from "../lib/settings";
import { tmdbImage, useTmdbEnabled } from "../lib/tmdb";
import { tmdbPeekId } from "../lib/tmdb-detail";
import { AddToArrButton } from "./AddToArrButton";
import { Switch } from "./controls";
import { FadeImage } from "./FadeImage";
import { PlayButton } from "./PlayButton";
import { GenreLine, ReleaseLine } from "./ReleaseLine";

const SLIDE_MS = 9000;

type FeaturedHeroProps = {
	entries: FeaturedEntry[];
	eyebrow: string;
};

/** Banner heights for the "top banner size" setting. */
const heroHeight = { full: "min-h-[78dvh]", compact: "min-h-[52dvh]" } as const;

/** The facts shown under the title: year, rating, and runtime or season count. */
function metaFor(item: BaseItemDto): string[] {
	const parts: string[] = [];
	if (item.ProductionYear) parts.push(String(item.ProductionYear));
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

const arrowButton =
	"glass absolute top-1/2 z-10 flex size-11 -translate-y-1/2 cursor-pointer items-center justify-center rounded-full text-ink opacity-0 shadow-card transition-all hover:scale-110 focus-visible:opacity-100 group-hover/hero:opacity-100 group-focus-within/hero:opacity-100 [@media(hover:none)]:hidden";

function Chevron({ direction }: { direction: "left" | "right" }) {
	return (
		<svg
			aria-hidden
			viewBox="0 0 24 24"
			className="size-5"
			fill="none"
			stroke="currentColor"
			strokeWidth="2.5"
			strokeLinecap="round"
			strokeLinejoin="round"
		>
			<path d={direction === "left" ? "M15 5l-7 7 7 7" : "M9 5l7 7-7 7"} />
		</svg>
	);
}

/** Full-bleed carousel of featured titles with crossfading backdrops. */
export function FeaturedHero({ entries, eyebrow }: FeaturedHeroProps) {
	const reduceMotion = useMotionReduced();
	const settings = useSettings();
	const tmdbOn = useTmdbEnabled();
	const arr = useArrAvailability();
	const peek = usePeek();
	const [index, setIndex] = useState(0);
	const [paused, setPaused] = useState(false);
	// The Add dialog is open: don't slide away from the title it is about.
	const [dialogOpen, setDialogOpen] = useState(false);

	const count = entries.length;
	const current = entries[index % count];

	const go = (direction: -1 | 1) =>
		setIndex((i) => (i + count + direction) % count);

	useEffect(() => {
		if (count < 2 || paused || dialogOpen || reduceMotion) return;
		const timer = window.setInterval(
			() => setIndex((i) => (i + 1) % count),
			SLIDE_MS,
		);
		return () => window.clearInterval(timer);
	}, [count, paused, dialogOpen, reduceMotion]);

	if (!current) return null;

	const owned = "owned" in current ? current.owned : undefined;
	const discover = "discover" in current ? current.discover : undefined;
	const logo = owned ? logoImage(owned, 640) : undefined;
	const title = owned?.Name ?? discover?.title ?? "";
	const meta = owned
		? metaFor(owned)
		: [
				discover?.year ? String(discover.year) : undefined,
				discover?.rating ? `★ ${discover.rating.toFixed(1)}` : undefined,
				discover?.kind === "tv" ? "TV series" : "Movie",
			].filter((part): part is string => Boolean(part));
	const genres = (owned?.Genres ?? []).slice(0, 3);
	const overview = owned?.Overview ?? discover?.overview;
	const popularLabel =
		discover || (owned && "popular" in current && current.popular)
			? "Popular right now"
			: undefined;
	// Only worth offering when the banner can contain titles the library doesn't have.
	const showDiscoverToggle = tmdbOn && settings.featuredSource !== "recent";

	return (
		<section
			aria-roledescription="carousel"
			aria-label="Featured"
			className={`group/hero relative isolate flex ${heroHeight[settings.heroStyle]} items-end overflow-hidden`}
			onMouseEnter={() => setPaused(true)}
			onMouseLeave={() => setPaused(false)}
			onFocus={() => setPaused(true)}
			onBlur={() => setPaused(false)}
		>
			{/* Backdrops stack on top of each other; only the current one is visible. */}
			{entries.map((entry, i) => {
				const src =
					"owned" in entry
						? backdropImage(entry.owned, 1920)
						: tmdbImage(entry.discover.backdrop, "w1280");
				if (!src) return null;
				return (
					<motion.div
						key={entry.key}
						aria-hidden
						className="absolute inset-0 -z-20"
						initial={false}
						animate={{ opacity: i === index % count ? 1 : 0 }}
						transition={{ duration: reduceMotion ? 0 : 1.1, ease: "easeInOut" }}
					>
						<FadeImage
							key={src}
							src={src}
							loading={i === 0 ? "eager" : "lazy"}
							className="size-full object-cover object-top"
						/>
					</motion.div>
				);
			})}

			{/* Keep text readable on the left and melt the bottom into the page */}
			<div
				aria-hidden
				className="absolute inset-0 -z-10 bg-linear-to-r from-canvas/85 via-canvas/30 to-transparent"
			/>
			<div
				aria-hidden
				className="absolute inset-x-0 bottom-0 -z-10 h-2/3 bg-linear-to-t from-canvas to-transparent"
			/>

			<div className="page-gutter w-full pb-20 pt-[calc(var(--nav-height)+4rem)]">
				<AnimatePresence mode="wait">
					<motion.div
						key={current.key}
						initial={{ opacity: 0, y: 20 }}
						animate={{ opacity: 1, y: 0 }}
						exit={{ opacity: 0, y: -8 }}
						transition={{
							duration: reduceMotion ? 0 : 0.6,
							ease: [0.16, 1, 0.3, 1],
						}}
						className="max-w-2xl"
					>
						<div className="mb-4 flex flex-wrap items-center gap-3">
							<p className="text-sm font-medium uppercase tracking-[0.2em] text-accent-strong">
								{popularLabel ?? eyebrow}
							</p>
							{discover && (
								<span className="glass rounded-pill px-3 py-1 text-xs font-semibold text-ink">
									Not in your library
								</span>
							)}
						</div>

						{logo ? (
							<FadeImage
								key={logo}
								src={logo}
								alt={title}
								loading="eager"
								className="max-h-28 w-auto max-w-full object-contain object-left sm:max-h-36"
							/>
						) : (
							<h1 className="text-4xl font-semibold leading-[1.05] tracking-tight sm:text-6xl">
								{title}
							</h1>
						)}

						{meta.length > 0 && (
							<p className="mt-5 text-sm font-medium text-ink-muted">
								{meta.join("  ·  ")}
							</p>
						)}
						{discover && <ReleaseLine key={discover.id} title={discover} />}
						{genres.length > 0 && <GenreLine genres={genres} />}

						<p className="mt-4 line-clamp-3 text-base text-ink/90 sm:text-lg">
							{overview}
						</p>

						<div className="mt-8 flex flex-wrap items-center gap-3">
							{owned ? (
								<>
									<PlayButton
										item={owned}
										className="flex cursor-pointer items-center gap-2 rounded-pill bg-ink px-6 py-3 text-sm font-semibold text-canvas transition-opacity hover:opacity-90"
									/>
									{owned.Id && (
										<button
											type="button"
											onClick={() => owned.Id && peek.open(owned.Id)}
											className="glass cursor-pointer rounded-pill px-6 py-3 text-sm font-semibold text-ink transition-colors hover:bg-glass-strong"
										>
											More info
										</button>
									)}
								</>
							) : discover ? (
								<>
									<AddToArrButton
										title={discover}
										variant="hero"
										onDialogChange={setDialogOpen}
									/>
									<button
										type="button"
										onClick={() =>
											peek.open(tmdbPeekId(discover.kind, discover.id))
										}
										className={
											arr[arrForKind(discover.kind)]
												? "glass cursor-pointer rounded-pill px-6 py-3 text-sm font-semibold text-ink transition-colors hover:bg-glass-strong"
												: "flex cursor-pointer items-center gap-2 rounded-pill bg-ink px-6 py-3 text-sm font-semibold text-canvas transition-opacity hover:opacity-90"
										}
									>
										Details
									</button>
								</>
							) : null}
						</div>
					</motion.div>
				</AnimatePresence>

				{(count > 1 || showDiscoverToggle) && (
					<div className="mt-10 flex flex-wrap items-center gap-x-8 gap-y-4">
						{count > 1 && (
							<div className="flex items-center gap-2">
								{entries.map((entry, i) => (
									<button
										key={entry.key}
										type="button"
										aria-label={`Show ${"owned" in entry ? (entry.owned.Name ?? `item ${i + 1}`) : entry.discover.title}`}
										aria-current={i === index % count}
										onClick={() => setIndex(i)}
										className={`h-1.5 cursor-pointer rounded-pill transition-all duration-300 ${
											i === index % count
												? "w-8 bg-ink"
												: "w-4 bg-ink/30 hover:bg-ink/60"
										}`}
									/>
								))}
							</div>
						)}
						{showDiscoverToggle && (
							<Switch
								checked={settings.featuredDiscover}
								onChange={(featuredDiscover) =>
									updateSettings({ featuredDiscover })
								}
								className="glass rounded-pill py-1.5 pl-4 pr-1.5"
							>
								<span className="pr-2 text-xs font-medium">
									Show popular titles I don't have
								</span>
							</Switch>
						)}
					</div>
				)}
			</div>

			{count > 1 && (
				<>
					<button
						type="button"
						aria-label="Previous"
						onClick={() => go(-1)}
						className={`${arrowButton} left-2 sm:left-3`}
					>
						<Chevron direction="left" />
					</button>
					<button
						type="button"
						aria-label="Next"
						onClick={() => go(1)}
						className={`${arrowButton} right-2 sm:right-3`}
					>
						<Chevron direction="right" />
					</button>
				</>
			)}
		</section>
	);
}

/** Same footprint as the hero, so the page doesn't jump when the featured items arrive. */
export function HeroSkeleton() {
	const { heroStyle } = useSettings();
	return (
		<section
			aria-hidden
			className={`relative isolate flex ${heroHeight[heroStyle]} items-end overflow-hidden`}
		>
			<div className="page-gutter w-full pb-20 pt-[calc(var(--nav-height)+4rem)]">
				<div className="shimmer h-4 w-40 rounded-pill" />
				<div className="shimmer mt-5 h-16 w-full max-w-md rounded-card" />
				<div className="shimmer mt-6 h-4 w-48 rounded-pill" />
				<div className="shimmer mt-4 h-16 w-full max-w-xl rounded-card" />
				<div className="mt-8 flex gap-3">
					<div className="shimmer h-11 w-28 rounded-pill" />
					<div className="shimmer h-11 w-32 rounded-pill" />
				</div>
			</div>
		</section>
	);
}
