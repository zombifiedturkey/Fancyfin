import { useSession } from "../auth/AuthProvider";
import { FeaturedHero, HeroSkeleton } from "../components/FeaturedHero";
import { Hero } from "../components/Hero";
import { MediaShelf } from "../components/MediaShelf";
import {
	useFeaturedItems,
	useLatestItems,
	useNextUpItems,
	useResumeItems,
} from "../lib/queries";

/** Home: a featured carousel, then Continue Watching, Next Up and Recently Added. */
export function Home() {
	const session = useSession();
	const featured = useFeaturedItems();
	const resume = useResumeItems();
	const nextUp = useNextUpItems();
	const latest = useLatestItems();

	const eyebrow = `Welcome back, ${session.userName}`;

	return (
		<>
			{featured.isPending ? (
				<HeroSkeleton />
			) : featured.data && featured.data.length > 0 ? (
				<FeaturedHero items={featured.data} eyebrow={eyebrow} />
			) : (
				// Empty library or the request failed: keep the page looking intentional.
				<Hero eyebrow={eyebrow} />
			)}

			<div className="relative z-10 -mt-6 pb-24">
				<MediaShelf
					title="Continue Watching"
					variant="landscape"
					items={resume.data}
					isLoading={resume.isPending}
					isError={resume.isError}
				/>
				<MediaShelf
					title="Next Up"
					variant="landscape"
					items={nextUp.data}
					isLoading={nextUp.isPending}
					isError={nextUp.isError}
				/>
				<MediaShelf
					title="Recently Added"
					variant="poster"
					items={latest.data}
					isLoading={latest.isPending}
					isError={latest.isError}
				/>
			</div>
		</>
	);
}
