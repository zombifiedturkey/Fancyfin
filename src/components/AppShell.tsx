import { Link, Outlet } from "@tanstack/react-router";
import { useState } from "react";
import { PeekHost } from "./PeekPanel";
import { SearchBox } from "./SearchBox";
import { UserMenu } from "./UserMenu";

const NAV_LINKS = [
	{ label: "Home", to: "/" as const },
	{ label: "Movies", to: "/movies" as const },
	{ label: "TV Shows", to: "/shows" as const },
];

const linkBase =
	"whitespace-nowrap rounded-pill px-3 py-1.5 text-sm transition-colors sm:px-3.5";

export function AppShell() {
	const [searchOpen, setSearchOpen] = useState(false);

	return (
		<div className="relative min-h-dvh bg-canvas">
			<header className="fixed inset-x-0 top-0 z-50">
				<div className="page-gutter pt-3">
					<nav
						aria-label="Primary"
						className="glass flex h-14 items-center gap-3 rounded-pill pl-4 pr-2.5 sm:gap-6 sm:pl-5"
					>
						<Link
							to="/"
							aria-label="Fancyfin home"
							className="flex items-center gap-2 text-[15px] font-semibold tracking-tight"
						>
							<span
								aria-hidden
								className="inline-block size-5 rounded-full bg-linear-to-br from-accent-strong to-accent"
							/>
							<span className="hidden sm:inline">Fancyfin</span>
						</Link>

						<ul
							className={`items-center gap-0.5 sm:ml-2 sm:gap-1 ${searchOpen ? "hidden md:flex" : "flex"}`}
						>
							{NAV_LINKS.map((item) => (
								<li key={item.label}>
									<Link
										to={item.to}
										activeOptions={{ exact: item.to === "/" }}
										className={`${linkBase} text-ink-muted hover:text-ink data-[status=active]:bg-glass-strong data-[status=active]:font-medium data-[status=active]:text-ink`}
									>
										{item.label}
									</Link>
								</li>
							))}
						</ul>

						<div className="ml-auto flex items-center gap-1">
							<SearchBox onOpenChange={setSearchOpen} />
							<UserMenu />
						</div>
					</nav>
				</div>
			</header>

			<main>
				<Outlet />
			</main>

			<PeekHost />
		</div>
	);
}
