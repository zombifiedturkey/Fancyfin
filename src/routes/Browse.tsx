import { useParams } from "@tanstack/react-router";
import { FeaturedHero, HeroSkeleton } from "../components/FeaturedHero";
import { GenreLink } from "../components/GenreLink";
import { KindNav } from "../components/KindNav";
import { LibraryGrid } from "../components/LibraryGrid";
import { MediaShelf } from "../components/MediaShelf";
import { useFeatured } from "../lib/featured-queries";
import {
	LIBRARY_COPY,
	type LibraryKind,
	SHELVES,
	type ShelfKey,
	type ShelfSpec,
} from "../lib/library";
import { useGenres, useShelf } from "../lib/queries";

/** How many genre rows the browse page shows. The Genres menu lists them all. */
const GENRE_ROWS = 8;

function Shelf({
	kind,
	spec,
	genre,
	title,
}: {
	kind: LibraryKind;
	spec: ShelfSpec;
	genre?: string;
	title?: string;
}) {
	const shelf = useShelf(kind, spec, genre);
	return (
		<MediaShelf
			title={title ?? spec.label}
			variant="poster"
			items={shelf.data}
			isLoading={shelf.isPending}
			isError={shelf.isError}
		/>
	);
}

function GenreRow({ kind, genre }: { kind: LibraryKind; genre: string }) {
	const shelf = useShelf(kind, SHELVES.recent, genre);
	return (
		<MediaShelf
			title={genre}
			variant="poster"
			items={shelf.data}
			isLoading={shelf.isPending}
			isError={shelf.isError}
			action={
				<GenreLink
					kind={kind}
					genre={genre}
					className="text-sm font-medium text-ink-muted transition-colors hover:text-ink"
				>
					See all
				</GenreLink>
			}
		/>
	);
}

/** The carousel at the top of a browse page, or a plain title if there's nothing to feature. */
function BrowseHero({ kind, genre }: { kind: LibraryKind; genre?: string }) {
	const copy = LIBRARY_COPY[kind];
	const featured = useFeatured({ kind, genre });

	if (featured.isPending) return <HeroSkeleton />;
	if (featured.data && featured.data.length > 0) {
		return (
			<FeaturedHero
				entries={featured.data}
				eyebrow={genre ? `${genre} ${copy.noun}s` : `New in ${copy.title}`}
			/>
		);
	}
	return (
		<div className="content-column pt-[calc(var(--nav-height)+5.5rem)] pb-4">
			<h1 className="text-4xl font-semibold tracking-tight sm:text-5xl">
				{genre ?? copy.title}
			</h1>
		</div>
	);
}

/** Hero first, with the Browse / Library / Genres bar floating over its top-left corner. */
function PageTop({ kind, genre }: { kind: LibraryKind; genre?: string }) {
	return (
		<div className="relative">
			<div className="page-gutter absolute inset-x-0 top-[calc(var(--nav-height)+1.25rem)] z-20">
				<KindNav kind={kind} genre={genre} />
			</div>
			<BrowseHero kind={kind} genre={genre} />
		</div>
	);
}

const PRESETS: ShelfKey[] = ["recent", "released", "rated"];

function Browse({ kind }: { kind: LibraryKind }) {
	const genres = useGenres(kind);

	return (
		<>
			<PageTop kind={kind} />
			<div className="relative z-10 -mt-6 pb-24">
				{PRESETS.map((key) => (
					<Shelf key={key} kind={kind} spec={SHELVES[key]} />
				))}
				{(genres.data ?? []).slice(0, GENRE_ROWS).map((genre) => (
					<GenreRow key={genre} kind={kind} genre={genre} />
				))}
			</div>
		</>
	);
}

function GenrePage({ kind, genre }: { kind: LibraryKind; genre: string }) {
	const copy = LIBRARY_COPY[kind];

	return (
		<>
			<PageTop kind={kind} genre={genre} />
			<div className="relative z-10 -mt-6 pb-24">
				{PRESETS.map((key) => (
					<Shelf
						key={key}
						kind={kind}
						spec={SHELVES[key]}
						genre={genre}
						title={`${SHELVES[key].label} in ${genre}`}
					/>
				))}
				<section className="content-column mt-10">
					<h2 className="mb-4 text-xl font-semibold tracking-tight">
						All {genre} {copy.noun}s
					</h2>
					<LibraryGrid kind={kind} fixedGenre={genre} />
				</section>
			</div>
		</>
	);
}

// Keyed so React never carries state from one page to another.
export function MoviesBrowsePage() {
	return <Browse key="movies" kind="movies" />;
}

export function ShowsBrowsePage() {
	return <Browse key="shows" kind="shows" />;
}

export function MoviesGenrePage() {
	const { genre = "" } = useParams({ strict: false });
	return <GenrePage key={`movies:${genre}`} kind="movies" genre={genre} />;
}

export function ShowsGenrePage() {
	const { genre = "" } = useParams({ strict: false });
	return <GenrePage key={`shows:${genre}`} kind="shows" genre={genre} />;
}
