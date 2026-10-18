import { motion } from "motion/react";
import { Aurora } from "./Aurora";

type HeroProps = {
	/** Small line above the headline. */
	eyebrow?: string;
};

/** Placeholder hero. In M2 this becomes the featured-item carousel with real backdrops. */
export function Hero({ eyebrow = "A client for Jellyfin" }: HeroProps) {
	return (
		<section className="relative isolate flex min-h-[78dvh] items-end overflow-hidden">
			<Aurora />
			{/* Fade into the page, like a full-bleed backdrop would */}
			<div
				aria-hidden
				className="absolute inset-x-0 bottom-0 -z-10 h-1/2 bg-linear-to-t from-canvas to-transparent"
			/>

			<div className="page-gutter w-full pb-16 pt-[calc(var(--nav-height)+4rem)]">
				<motion.div
					initial={{ opacity: 0, y: 24 }}
					animate={{ opacity: 1, y: 0 }}
					transition={{ duration: 0.9, ease: [0.16, 1, 0.3, 1] }}
				>
					<p className="mb-4 text-sm font-medium uppercase tracking-[0.2em] text-accent-strong">
						{eyebrow}
					</p>
					<h1 className="max-w-3xl text-5xl font-semibold leading-[1.02] tracking-tight sm:text-7xl">
						Your library,
						<br />
						beautifully.
					</h1>
					<p className="mt-6 max-w-xl text-lg text-ink-muted">
						Big artwork, smooth motion, and a cinematic way to browse everything
						you own.
					</p>
					<div className="mt-8 flex flex-wrap items-center gap-3">
						<button
							type="button"
							disabled
							className="rounded-pill bg-ink px-6 py-3 text-sm font-semibold text-canvas opacity-60"
						>
							Play
						</button>
						<button
							type="button"
							disabled
							className="glass rounded-pill px-6 py-3 text-sm font-semibold text-ink opacity-60"
						>
							More info
						</button>
					</div>
				</motion.div>
			</div>
		</section>
	);
}
