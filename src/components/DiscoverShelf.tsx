import { useDiscover } from "../lib/featured-queries";
import { hueFor } from "../lib/hue";
import { type TmdbTitle, tmdbImage, tmdbPage } from "../lib/tmdb";
import { AddToArrButton } from "./AddToArrButton";
import { FadeImage } from "./FadeImage";
import { cardWidth } from "./MediaCard";
import { Shelf } from "./Shelf";

function DiscoverCard({ title }: { title: TmdbTitle }) {
	const hue = hueFor(title.title);
	return (
		<div>
			<a
				href={tmdbPage(title)}
				target="_blank"
				rel="noopener noreferrer"
				className="group/poster block"
			>
				<div
					className="relative aspect-[2/3] overflow-hidden rounded-card border border-glass-border shadow-card transition-transform duration-300 group-hover/poster:-translate-y-1.5 group-focus-visible/poster:-translate-y-1.5"
					style={{
						backgroundImage: `linear-gradient(145deg, hsl(${hue} 45% 28%), hsl(${(hue + 50) % 360} 50% 14%))`,
					}}
				>
					{title.poster && (
						<FadeImage
							key={title.poster}
							src={tmdbImage(title.poster, "w342")}
							alt=""
							className="absolute inset-0 size-full object-cover"
						/>
					)}
					<span className="glass absolute left-2 top-2 rounded-pill px-2 py-0.5 text-[11px] font-semibold">
						{title.kind === "tv" ? "Series" : "Movie"}
					</span>
				</div>
				<p className="mt-3 truncate text-sm font-medium">{title.title}</p>
				{title.year && (
					<p className="truncate text-xs text-ink-muted">{title.year}</p>
				)}
			</a>
			<div className="mt-2.5">
				<AddToArrButton title={title} variant="card" />
			</div>
		</div>
	);
}

/**
 * Popular titles the library doesn't have, each with an Add button. Only for administrators on a
 * server where Radarr or Sonarr is set up; for everyone else it isn't there at all.
 */
export function DiscoverShelf() {
	const discover = useDiscover();
	const titles = discover.data ?? [];
	if (titles.length === 0) return null;

	return (
		<Shelf title="Discover">
			{titles.map((title) => (
				<li
					key={`${title.kind}:${title.id}`}
					className={`${cardWidth.poster} shrink-0`}
				>
					<DiscoverCard title={title} />
				</li>
			))}
		</Shelf>
	);
}
