import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import type { ReactNode } from "react";
import { cardWidth, MediaCard } from "./MediaCard";
import { Shelf } from "./Shelf";

type MediaShelfProps = {
	title: string;
	variant: "poster" | "landscape";
	items: BaseItemDto[] | undefined;
	isLoading: boolean;
	isError: boolean;
	action?: ReactNode;
};

/** A shelf of real items: skeleton while loading, nothing at all when there's nothing to show. */
export function MediaShelf({
	title,
	variant,
	items,
	isLoading,
	isError,
	action,
}: MediaShelfProps) {
	const width = cardWidth[variant];

	if (isLoading) {
		return (
			<Shelf title={title} action={action}>
				{Array.from({ length: 8 }, (_, i) => (
					<li key={i} className={`${width} shrink-0`}>
						<div
							className={`shimmer rounded-card border border-glass-border ${
								variant === "poster" ? "aspect-[2/3]" : "aspect-video"
							}`}
						/>
					</li>
				))}
			</Shelf>
		);
	}

	if (isError) {
		return (
			<Shelf title={title} action={action}>
				<li className="text-sm text-ink-muted">
					Couldn't load this row. Try refreshing.
				</li>
			</Shelf>
		);
	}

	const visible = (items ?? []).filter((item) => item.Id);
	if (visible.length === 0) return null;

	return (
		<Shelf title={title} action={action}>
			{visible.map((item) => (
				<li key={item.Id} className={`${width} shrink-0`}>
					<MediaCard item={item} variant={variant} />
				</li>
			))}
		</Shelf>
	);
}
