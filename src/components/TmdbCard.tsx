import { hueFor } from "../lib/hue";
import { usePeek } from "../lib/peek";
import { type TmdbTitle, tmdbImage } from "../lib/tmdb";
import { tmdbPeekId } from "../lib/tmdb-detail";
import { AddToArrButton } from "./AddToArrButton";
import { FadeImage } from "./FadeImage";

/**
 * A poster for a title the library doesn't have. Clicking it opens the same floating panel as any
 * other title, with the details from TMDB, and there is an Add button under it.
 */
export function TmdbCard({ title }: { title: TmdbTitle }) {
	const peek = usePeek();
	const hue = hueFor(title.title);
	return (
		<div>
			<button
				type="button"
				onClick={() => peek.open(tmdbPeekId(title.kind, title.id))}
				className="group/poster block w-full cursor-pointer text-left"
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
			</button>
			<div className="mt-2.5">
				<AddToArrButton title={title} variant="card" />
			</div>
		</div>
	);
}
