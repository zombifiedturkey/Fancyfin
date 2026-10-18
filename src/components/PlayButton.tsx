import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import { Link } from "@tanstack/react-router";
import { formatRuntime, minutesLeft } from "../lib/format";
import { usePlayTarget } from "../lib/playback";

function PlayIcon() {
	return (
		<svg aria-hidden viewBox="0 0 24 24" className="size-4" fill="currentColor">
			<path d="M7 4.5v15l13-7.5z" />
		</svg>
	);
}

/** Play or Resume for a title; for a series it starts the next episode you haven't seen. */
export function PlayButton({
	item,
	className,
}: {
	item: BaseItemDto;
	className: string;
}) {
	const target = usePlayTarget(item);

	let label = "Play";
	if (item.Type === "Series") {
		if (target?.label)
			label = `${target.start > 0 ? "Resume" : "Play"} ${target.label}`;
	} else if (target && target.start > 0) {
		const left = formatRuntime(minutesLeft(item));
		label = left ? `Resume · ${left} left` : "Resume";
	}

	if (!target) {
		return (
			<button type="button" disabled className={`${className} opacity-60`}>
				<PlayIcon />
				{label}
			</button>
		);
	}

	return (
		<Link
			to="/watch/$id"
			params={{ id: target.id }}
			search={{ start: Math.floor(target.start) }}
			className={className}
		>
			<PlayIcon />
			{label}
		</Link>
	);
}
