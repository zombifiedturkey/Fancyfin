import { Navigate } from "@tanstack/react-router";
import { formatRuntime } from "../lib/format";
import { hueFor } from "../lib/hue";
import { formatReleaseDate } from "../lib/release";
import { type TmdbKind, type TmdbTitle, tmdbImage } from "../lib/tmdb";
import type { TmdbDetail } from "../lib/tmdb-detail";
import { useOwnedId, useTmdbDetail } from "../lib/tmdb-detail-queries";
import { AddToArrButton } from "./AddToArrButton";
import { FadeImage } from "./FadeImage";
import { cardWidth } from "./MediaCard";
import { chip, PanelSkeleton, Score } from "./PeekParts";
import { PersonView, personSlot } from "./PeopleShelf";
import { ReleasePill } from "./ReleaseLine";
import { Shelf } from "./Shelf";
import { TmdbCard } from "./TmdbCard";

/** The facts shown as chips under the title. */
function factsFor(detail: TmdbDetail): string[] {
	const parts: string[] = [];
	if (detail.year) parts.push(String(detail.year));
	if (detail.certification) parts.push(detail.certification);
	if (detail.kind === "tv" && detail.seasons) {
		parts.push(detail.seasons === 1 ? "1 season" : `${detail.seasons} seasons`);
	}
	const runtime = formatRuntime(detail.runtime);
	if (runtime)
		parts.push(detail.kind === "tv" ? `${runtime} episodes` : runtime);
	return parts;
}

function asTitle(detail: TmdbDetail): TmdbTitle {
	return {
		id: detail.id,
		kind: detail.kind,
		title: detail.title,
		overview: detail.overview,
		backdrop: detail.backdrop ?? "",
		poster: detail.poster,
		year: detail.year,
		rating: detail.rating,
		popularity: 0,
	};
}

/** Creators or director, studio, genres and the like, laid out like the library's Details. */
function Details({ detail }: { detail: TmdbDetail }) {
	const entries: { label: string; value: string }[] = [];
	const add = (label: string, values: (string | undefined)[]) => {
		const present = values.filter((value): value is string => Boolean(value));
		if (present.length > 0) entries.push({ label, value: present.join(", ") });
	};
	add(detail.kind === "tv" ? "Created by" : "Director", detail.makers);
	add(detail.kind === "tv" ? "Network" : "Studio", detail.studios);
	add("Genres", detail.genres);
	add("Status", [detail.status]);
	add("Release date", [
		detail.date ? formatReleaseDate(Date.parse(detail.date)) : undefined,
	]);
	if (detail.kind === "tv" && detail.episodes) {
		add("Episodes", [String(detail.episodes)]);
	}
	add("Language", [detail.language]);

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
				<div>
					<dt className="text-xs font-medium uppercase tracking-wider text-ink-faint">
						More information
					</dt>
					<dd className="mt-1 text-sm">
						<a
							href={`https://www.themoviedb.org/${detail.kind}/${detail.id}`}
							target="_blank"
							rel="noopener noreferrer"
							className="text-accent-strong hover:underline"
						>
							The Movie Database ↗
						</a>
					</dd>
				</div>
			</dl>
		</section>
	);
}

/**
 * The floating panel's contents for a title the library doesn't have: the same layout as a library
 * title, filled from TMDB, with the Radarr or Sonarr button where Play would be.
 */
export function TmdbPeekContent({ kind, id }: { kind: TmdbKind; id: number }) {
	const owned = useOwnedId(kind, id);
	const query = useTmdbDetail(kind, id);

	if (owned.isPending || query.isPending) return <PanelSkeleton />;

	// The library has it after all (for instance from a "More like this" row): open the real panel.
	if (owned.data) {
		const itemId = owned.data;
		return (
			<Navigate
				to="."
				search={
					((prev: Record<string, unknown>) => ({
						...prev,
						peek: itemId,
					})) as never
				}
				replace
			/>
		);
	}

	const detail = query.data;
	if (!detail) {
		return (
			<p className="page-gutter pt-40 text-ink-muted">
				We couldn't load that title from TMDB.
			</p>
		);
	}

	const title = asTitle(detail);
	const backdrop = detail.backdrop
		? tmdbImage(detail.backdrop, "w1280")
		: undefined;
	const poster = detail.poster ? tmdbImage(detail.poster, "w500") : undefined;
	const hue = hueFor(detail.title);
	const chips = [...factsFor(detail), ...detail.genres.slice(0, 4)];

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
							<p className="mb-3 flex flex-wrap items-center gap-3">
								<span className="text-sm font-medium uppercase tracking-[0.2em] text-accent-strong">
									{kind === "tv" ? "TV series" : "Movie"}
								</span>
								<span className="glass rounded-pill px-3 py-1 text-xs font-semibold text-ink">
									Not in your library
								</span>
							</p>
							<h2 className="text-3xl font-semibold leading-[1.05] tracking-tight sm:text-5xl">
								{detail.title}
							</h2>
							{detail.tagline && (
								<p className="mt-3 text-sm italic text-ink-muted sm:text-base">
									{detail.tagline}
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

					{detail.release && <ReleasePill release={detail.release} />}

					{detail.rating && (
						<div className="mt-7 flex flex-wrap gap-3">
							<Score label="TMDB" value={`★ ${detail.rating.toFixed(1)}`} />
						</div>
					)}

					{detail.overview && (
						<p className="mt-7 max-w-3xl text-base leading-relaxed text-ink/90 sm:text-lg">
							{detail.overview}
						</p>
					)}

					<div className="mt-8 flex flex-wrap items-center gap-3">
						<AddToArrButton title={title} variant="hero" />
					</div>
				</div>
			</div>

			<div className="pb-12">
				<div className="mt-6">
					{detail.cast.length > 0 && (
						<Shelf title="Cast">
							{detail.cast.map((person) => (
								<li key={`${person.id}:${person.role}`} className={personSlot}>
									<PersonView
										name={person.name}
										role={person.role}
										src={
											person.photo ? tmdbImage(person.photo, "w342") : undefined
										}
									/>
								</li>
							))}
						</Shelf>
					)}
					{detail.related.length > 0 && (
						<Shelf title="More like this">
							{detail.related.map((related) => (
								<li
									key={`${related.kind}:${related.id}`}
									className={`${cardWidth.poster} shrink-0`}
								>
									<TmdbCard title={related} />
								</li>
							))}
						</Shelf>
					)}
				</div>

				<Details detail={detail} />
			</div>
		</>
	);
}
