import {
	createRootRoute,
	createRoute,
	createRouter,
	Outlet,
} from "@tanstack/react-router";
import { AppLayout } from "./routes/AppLayout";
import {
	MoviesBrowsePage,
	MoviesGenrePage,
	ShowsBrowsePage,
	ShowsGenrePage,
} from "./routes/Browse";
import { Home } from "./routes/Home";
import { ItemPage } from "./routes/Item";
import { MoviesLibraryPage, ShowsLibraryPage } from "./routes/Library";
import { Login } from "./routes/Login";
import { WatchPage } from "./routes/Watch";

// ?peek=<id> on any page opens that title in the floating detail panel.
const rootRoute = createRootRoute({
	component: Outlet,
	validateSearch: (search: Record<string, unknown>): { peek?: string } =>
		typeof search.peek === "string" && search.peek ? { peek: search.peek } : {},
});

const loginRoute = createRoute({
	getParentRoute: () => rootRoute,
	path: "/login",
	component: Login,
});

// Full-screen player: signed-in only, but without the nav shell. ?start=<seconds> resumes part way in.
const watchRoute = createRoute({
	getParentRoute: () => rootRoute,
	path: "/watch/$id",
	component: WatchPage,
	validateSearch: (search: Record<string, unknown>): { start: number } => {
		const start = Number(search.start);
		return { start: Number.isFinite(start) && start > 0 ? start : 0 };
	},
});

// Pathless layout route: everything under it needs a signed-in user.
const appRoute = createRoute({
	getParentRoute: () => rootRoute,
	id: "app",
	component: AppLayout,
});

const homeRoute = createRoute({
	getParentRoute: () => appRoute,
	path: "/",
	component: Home,
});

const moviesRoute = createRoute({
	getParentRoute: () => appRoute,
	path: "/movies",
	component: MoviesBrowsePage,
});

const moviesLibraryRoute = createRoute({
	getParentRoute: () => appRoute,
	path: "/movies/library",
	component: MoviesLibraryPage,
});

const moviesGenreRoute = createRoute({
	getParentRoute: () => appRoute,
	path: "/movies/genre/$genre",
	component: MoviesGenrePage,
});

const showsRoute = createRoute({
	getParentRoute: () => appRoute,
	path: "/shows",
	component: ShowsBrowsePage,
});

const showsLibraryRoute = createRoute({
	getParentRoute: () => appRoute,
	path: "/shows/library",
	component: ShowsLibraryPage,
});

const showsGenreRoute = createRoute({
	getParentRoute: () => appRoute,
	path: "/shows/genre/$genre",
	component: ShowsGenrePage,
});

const itemRoute = createRoute({
	getParentRoute: () => appRoute,
	path: "/item/$id",
	component: ItemPage,
});

const routeTree = rootRoute.addChildren([
	loginRoute,
	watchRoute,
	appRoute.addChildren([
		homeRoute,
		moviesRoute,
		moviesLibraryRoute,
		moviesGenreRoute,
		showsRoute,
		showsLibraryRoute,
		showsGenreRoute,
		itemRoute,
	]),
]);

export const router = createRouter({
	routeTree,
	defaultPreload: "intent",
	scrollRestoration: true,
});

declare module "@tanstack/react-router" {
	interface Register {
		router: typeof router;
	}
}
