import { useDiscover } from "../lib/featured-queries";
import { cardWidth } from "./MediaCard";
import { Shelf } from "./Shelf";
import { TmdbCard } from "./TmdbCard";

/**
 * Popular titles the library doesn't have, each with an Add button. Only there when Radarr or Sonarr
 * is connected and working.
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
					<TmdbCard title={title} />
				</li>
			))}
		</Shelf>
	);
}
