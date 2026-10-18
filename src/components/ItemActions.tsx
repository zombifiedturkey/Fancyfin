import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import { useSetFavorite, useSetPlayed } from "../lib/item-queries";
import { ManageMenu } from "./ManageMenu";
import { PlayButton } from "./PlayButton";

const iconButton =
	"glass flex h-11 cursor-pointer items-center gap-2 rounded-pill px-5 text-sm font-semibold text-ink transition-colors hover:bg-glass-strong disabled:cursor-wait disabled:opacity-60";

function Check() {
	return (
		<svg
			aria-hidden
			viewBox="0 0 24 24"
			className="size-4"
			fill="none"
			stroke="currentColor"
			strokeWidth="3"
			strokeLinecap="round"
			strokeLinejoin="round"
		>
			<path d="M5 12.5l4.5 4.5L19 7.5" />
		</svg>
	);
}

function Heart({ filled }: { filled: boolean }) {
	return (
		<svg
			aria-hidden
			viewBox="0 0 24 24"
			className="size-4"
			fill={filled ? "currentColor" : "none"}
			stroke="currentColor"
			strokeWidth="2.5"
			strokeLinejoin="round"
		>
			<path d="M12 20.5s-7.5-4.6-9.2-9.4C1.7 7.7 3.7 4.5 7 4.5c2 0 3.6 1.1 5 3 1.4-1.9 3-3 5-3 3.3 0 5.3 3.2 4.2 6.6-1.7 4.8-9.2 9.4-9.2 9.4z" />
		</svg>
	);
}

/** Play, watched and favourite buttons for a title. */
export function ItemActions({
	item,
	itemId,
}: {
	item: BaseItemDto;
	itemId: string;
}) {
	const setPlayed = useSetPlayed(itemId);
	const setFavorite = useSetFavorite(itemId);

	const played = item.UserData?.Played ?? false;
	const favorite = item.UserData?.IsFavorite ?? false;

	return (
		<div className="mt-8 flex flex-wrap items-center gap-3">
			<PlayButton
				item={item}
				className="flex h-11 cursor-pointer items-center gap-2 rounded-pill bg-ink px-7 text-sm font-semibold text-canvas transition-opacity hover:opacity-90"
			/>

			<button
				type="button"
				aria-pressed={played}
				disabled={setPlayed.isPending}
				onClick={() => setPlayed.mutate(!played)}
				className={iconButton}
			>
				<Check />
				{played ? "Watched" : "Mark watched"}
			</button>

			<button
				type="button"
				aria-pressed={favorite}
				disabled={setFavorite.isPending}
				onClick={() => setFavorite.mutate(!favorite)}
				className={iconButton}
			>
				<Heart filled={favorite} />
				{favorite ? "Favorited" : "Favorite"}
			</button>

			<ManageMenu item={item} itemId={itemId} />
		</div>
	);
}
