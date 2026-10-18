import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import { motion } from "motion/react";
import { clampPercent, landscapeSubtitle } from "../lib/format";
import { hueFor } from "../lib/hue";
import { landscapeImage, posterImage } from "../lib/images";
import { usePeek } from "../lib/peek";
import { FadeImage } from "./FadeImage";

type MediaCardProps = {
	item: BaseItemDto;
	/** Portrait poster (2:3) or wide still (16:9). */
	variant: "poster" | "landscape";
	/** Override what clicking does. By default a card opens the title's floating panel. */
	onSelect?: (item: BaseItemDto) => void;
};

/** Card widths per variant. The shelf row sizes its items with these too. */
export const cardWidth = {
	poster: "w-36 sm:w-44 lg:w-52",
	landscape: "w-64 sm:w-72 lg:w-80",
} as const;

export function MediaCard({ item, variant, onSelect }: MediaCardProps) {
	const peek = usePeek();
	const isPoster = variant === "poster";
	// Episodes lead with the show's name; the episode itself goes on the second line.
	const title =
		(item.Type === "Episode" ? item.SeriesName : item.Name) ??
		item.Name ??
		"Untitled";
	const subtitle = isPoster
		? item.ProductionYear
			? String(item.ProductionYear)
			: undefined
		: landscapeSubtitle(item);

	const src = isPoster ? posterImage(item, 400) : landscapeImage(item, 640);
	const hue = hueFor(title);

	const unplayed =
		item.Type === "Series" ? (item.UserData?.UnplayedItemCount ?? 0) : 0;
	const progress = isPoster ? 0 : clampPercent(item.UserData?.PlayedPercentage);

	return (
		<motion.button
			type="button"
			onClick={() => {
				if (onSelect) onSelect(item);
				else if (item.Id) peek.open(item.Id);
			}}
			className="block w-full cursor-pointer text-left outline-none"
			whileHover={{ scale: 1.05, y: -6 }}
			whileFocus={{ scale: 1.05, y: -6 }}
			whileTap={{ scale: 0.98 }}
			transition={{ type: "spring", stiffness: 320, damping: 26 }}
		>
			<div
				className={`relative overflow-hidden rounded-card border border-glass-border shadow-card ${
					isPoster ? "aspect-[2/3]" : "aspect-video"
				}`}
				style={{
					backgroundImage: `linear-gradient(145deg, hsl(${hue} 45% 28%), hsl(${(hue + 50) % 360} 50% 14%))`,
				}}
			>
				{/* Shown when there's no picture, or while it loads */}
				<span
					aria-hidden
					className="absolute inset-0 flex items-end p-3 text-sm font-semibold leading-tight text-white/70"
				>
					{title}
				</span>
				{src && (
					<FadeImage
						key={src}
						src={src}
						className="absolute inset-0 size-full object-cover"
					/>
				)}

				{unplayed > 0 && (
					<span className="glass absolute right-2 top-2 rounded-pill px-2 py-0.5 text-xs font-semibold">
						{unplayed}
					</span>
				)}

				{progress > 0 && (
					<div className="absolute inset-x-0 bottom-0 h-1 bg-black/50">
						<div
							className="h-full bg-accent"
							style={{ width: `${progress}%` }}
						/>
					</div>
				)}
			</div>

			<p className="mt-3 truncate text-sm font-medium">{title}</p>
			{subtitle && (
				<p className="truncate text-xs text-ink-muted">{subtitle}</p>
			)}
		</motion.button>
	);
}
