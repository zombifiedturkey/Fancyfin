import { KindNav } from "../components/KindNav";
import { LibraryGrid } from "../components/LibraryGrid";
import { LIBRARY_COPY, type LibraryKind } from "../lib/library";

/** The full, sortable grid of everything in a library. */
function Library({ kind }: { kind: LibraryKind }) {
	return (
		<div className="content-column pb-24 pt-[calc(var(--nav-height)+2.5rem)]">
			<h1 className="text-4xl font-semibold tracking-tight sm:text-5xl">
				{LIBRARY_COPY[kind].title}
			</h1>
			<div className="mt-5">
				<KindNav kind={kind} />
			</div>
			<div className="mt-6">
				<LibraryGrid kind={kind} />
			</div>
		</div>
	);
}

// Keyed so React never carries one library's state over to the other.
export function MoviesLibraryPage() {
	return <Library key="movies" kind="movies" />;
}

export function ShowsLibraryPage() {
	return <Library key="shows" kind="shows" />;
}
