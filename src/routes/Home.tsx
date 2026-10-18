import { useSession } from "../auth/AuthProvider";
import { FeaturedHero, HeroSkeleton } from "../components/FeaturedHero";
import { Hero } from "../components/Hero";
import { MediaShelf } from "../components/MediaShelf";
import { useFeatured } from "../lib/featured-queries";
import { useLatestItems, useNextUpItems, useResumeItems } from "../lib/queries";
import { useSettings } from "../lib/settings";
import { HOME_ROW_LABELS } from "../lib/settings-model";

/** Home: a featured banner, then the rows chosen (and ordered) in Settings. */
export function Home() {
	const session = useSession();
	const settings = useSettings();
	const featured = useFeatured();
	const resume = useResumeItems();
	const nextUp = useNextUpItems();
	const latest = useLatestItems();

	const eyebrow = `Welcome back, ${session.userName}`;

	const rows = {
		continue: { variant: "landscape", query: resume },
		nextup: { variant: "landscape", query: nextUp },
		recent: { variant: "poster", query: latest },
	} as const;

	return (
		<>
			{featured.isPending ? (
				<HeroSkeleton />
			) : featured.data && featured.data.length > 0 ? (
				<FeaturedHero entries={featured.data} eyebrow={eyebrow} />
			) : (
				// Empty library or the request failed: keep the page looking intentional.
				<Hero eyebrow={eyebrow} />
			)}

			<div className="relative z-10 -mt-6 pb-24">
				{settings.homeRows
					.filter((row) => row.visible)
					.map((row) => {
						const { variant, query } = rows[row.id];
						return (
							<MediaShelf
								key={row.id}
								title={HOME_ROW_LABELS[row.id]}
								variant={variant}
								items={query.data}
								isLoading={query.isPending}
								isError={query.isError}
							/>
						);
					})}
			</div>
		</>
	);
}
