import { Link, Navigate, useParams } from "@tanstack/react-router";
import { Aurora } from "../components/Aurora";
import { EpisodeList } from "../components/EpisodeList";
import { FadeImage } from "../components/FadeImage";
import { HeroSkeleton } from "../components/FeaturedHero";
import { ItemActions } from "../components/ItemActions";
import { factsFor, ItemDetails } from "../components/ItemDetails";
import { MediaShelf } from "../components/MediaShelf";
import { PeopleShelf } from "../components/PeopleShelf";
import { backdropImage, logoImage } from "../lib/images";
import { castOf } from "../lib/item-info";
import { useItem, useSimilarItems } from "../lib/item-queries";

function NotFound() {
	return (
		<div className="content-column pt-[calc(var(--nav-height)+6rem)]">
			<h1 className="text-3xl font-semibold tracking-tight">
				We couldn't find that title
			</h1>
			<p className="mt-3 text-ink-muted">
				It may have been removed from your library.
			</p>
			<Link
				to="/"
				className="glass mt-6 inline-block rounded-pill px-5 py-2 text-sm font-medium"
			>
				Back to Home
			</Link>
		</div>
	);
}

function ItemView({ itemId }: { itemId: string }) {
	const item = useItem(itemId);
	const type = item.data?.Type;
	const similar = useSimilarItems(
		itemId,
		type === "Movie" || type === "Series",
	);

	if (item.isPending) return <HeroSkeleton />;
	const data = item.data;
	if (!data) return <NotFound />;

	// Season pages are just a view of their show.
	if (data.Type === "Season" && data.SeriesId) {
		return <Navigate to="/item/$id" params={{ id: data.SeriesId }} replace />;
	}

	const isEpisode = data.Type === "Episode";
	const backdrop = backdropImage(data, 1920);
	const logo = isEpisode ? undefined : logoImage(data, 640);
	const meta = [
		...factsFor(data),
		...(data.CommunityRating ? [`★ ${data.CommunityRating.toFixed(1)}`] : []),
	];
	const tagline = data.Taglines?.[0];

	return (
		<>
			<section className="relative isolate flex min-h-[70dvh] items-end overflow-hidden">
				{backdrop ? (
					<FadeImage
						key={backdrop}
						src={backdrop}
						loading="eager"
						className="absolute inset-0 -z-20 size-full object-cover object-top"
					/>
				) : (
					<Aurora />
				)}
				<div
					aria-hidden
					className="absolute inset-0 -z-10 bg-linear-to-r from-canvas/85 via-canvas/30 to-transparent"
				/>
				<div
					aria-hidden
					className="absolute inset-x-0 bottom-0 -z-10 h-2/3 bg-linear-to-t from-canvas to-transparent"
				/>

				<div className="page-gutter w-full pb-12 pt-[calc(var(--nav-height)+4rem)]">
					<div className="max-w-2xl">
						<p className="mb-4 text-sm font-medium uppercase tracking-[0.2em] text-accent-strong">
							{isEpisode && data.SeriesId ? (
								<Link
									to="/item/$id"
									params={{ id: data.SeriesId }}
									className="hover:underline"
								>
									{data.SeriesName}
								</Link>
							) : data.Type === "Series" ? (
								"TV Series"
							) : (
								"Movie"
							)}
						</p>

						{logo ? (
							<FadeImage
								key={logo}
								src={logo}
								alt={data.Name ?? ""}
								loading="eager"
								className="max-h-28 w-auto max-w-full object-contain object-left sm:max-h-36"
							/>
						) : (
							<h1 className="text-4xl font-semibold leading-[1.05] tracking-tight sm:text-6xl">
								{data.Name}
							</h1>
						)}

						{meta.length > 0 && (
							<p className="mt-5 text-sm font-medium text-ink-muted">
								{meta.join("  ·  ")}
							</p>
						)}
						{tagline && (
							<p className="mt-3 text-sm italic text-ink-muted">{tagline}</p>
						)}
						{data.Overview && (
							<p className="mt-4 text-base text-ink/90 sm:text-lg">
								{data.Overview}
							</p>
						)}

						<ItemActions item={data} itemId={itemId} />
					</div>
				</div>
			</section>

			<div className="relative z-10 pb-24">
				{data.Type === "Series" && <EpisodeList seriesId={data.Id ?? itemId} />}
				{isEpisode && data.SeriesId && (
					<EpisodeList
						seriesId={data.SeriesId}
						seasonId={data.SeasonId}
						currentEpisodeId={data.Id}
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

export function ItemPage() {
	const { id = "" } = useParams({ strict: false });
	// Keyed so moving from one title to another starts fresh.
	return <ItemView key={id} itemId={id} />;
}
