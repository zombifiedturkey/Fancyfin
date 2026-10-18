import { Link } from "@tanstack/react-router";
import type { ReactNode } from "react";
import type { LibraryKind } from "../lib/library";

type GenreLinkProps = {
	kind: LibraryKind;
	genre: string;
	className?: string;
	onClick?: () => void;
	children: ReactNode;
};

/** A link to a genre's page within Movies or TV Shows. */
export function GenreLink({ kind, genre, ...rest }: GenreLinkProps) {
	return kind === "movies" ? (
		<Link to="/movies/genre/$genre" params={{ genre }} {...rest} />
	) : (
		<Link to="/shows/genre/$genre" params={{ genre }} {...rest} />
	);
}
