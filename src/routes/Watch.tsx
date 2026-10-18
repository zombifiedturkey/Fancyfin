import type { MediaSourceInfo } from "@jellyfin/sdk/lib/generated-client/models/media-source-info";
import { useQueryClient } from "@tanstack/react-query";
import {
	getRouteApi,
	useCanGoBack,
	useNavigate,
	useRouter,
} from "@tanstack/react-router";
import { useCallback, useEffect, useState } from "react";
import { RequireAuth } from "../auth/RequireAuth";
import { Player } from "../components/Player";
import { useItem } from "../lib/item-queries";
import { usePlaybackPlan } from "../lib/playback";
import {
	defaultSubtitleIndex,
	saveSubtitlePref,
	subtitleKind,
	subtitleStreams,
} from "../lib/subtitles";

const route = getRouteApi("/watch/$id");

function Backdrop({ children }: { children: React.ReactNode }) {
	return (
		<div className="fixed inset-0 z-50 flex items-center justify-center bg-black p-6 text-center">
			{children}
		</div>
	);
}

function WatchInner() {
	const { id } = route.useParams();
	const { start } = route.useSearch();
	const router = useRouter();
	const canGoBack = useCanGoBack();
	const navigate = useNavigate();
	const queryClient = useQueryClient();

	const item = useItem(id);
	const [directOk, setDirectOk] = useState(true);
	const [resumeAt, setResumeAt] = useState<number | null>(null);
	const [audioIndex, setAudioIndex] = useState<number | undefined>(undefined);
	// null until the first plan arrives and we know which tracks exist.
	const [subIndex, setSubIndex] = useState<number | null>(null);

	// A picture subtitle can only be shown by burning it into the video, which means transcoding.
	const [lastSource, setLastSource] = useState<MediaSourceInfo | null>(null);
	const chosenStream =
		subIndex !== null && subIndex >= 0 && lastSource
			? subtitleStreams(lastSource).find((s) => s.Index === subIndex)
			: undefined;
	const burnIndex =
		chosenStream && subtitleKind(chosenStream) === "image"
			? chosenStream.Index
			: undefined;

	const plan = usePlaybackPlan(id, {
		allowDirect:
			directOk && audioIndex === undefined && burnIndex === undefined,
		audioIndex,
		burnIndex,
	});
	const current = plan.data && plan.data.itemId === id ? plan.data : undefined;

	// New title (e.g. next episode): start fresh.
	useEffect(() => {
		setDirectOk(true);
		setResumeAt(null);
		setAudioIndex(undefined);
		setSubIndex(null);
		setLastSource(null);
	}, [id]);

	// Once the first plan is known, pick the starting subtitle from what the user chose last time.
	useEffect(() => {
		if (!current) return;
		setLastSource(current.source);
		if (subIndex === null) setSubIndex(defaultSubtitleIndex(current.source));
	}, [current, subIndex]);

	// Refresh Continue Watching and watched marks when leaving the player.
	useEffect(() => {
		return () => {
			window.setTimeout(() => {
				void queryClient.invalidateQueries({
					predicate: (query) => query.queryKey[0] !== "playback-plan",
				});
			}, 1500);
		};
	}, [queryClient]);

	const back = useCallback(() => {
		if (canGoBack) router.history.back();
		else void navigate({ to: "/item/$id", params: { id } });
	}, [canGoBack, router, navigate, id]);

	const selectAudio = useCallback(
		(index: number, at: number) => {
			const fallback = current?.source.DefaultAudioStreamIndex;
			setResumeAt(at);
			setAudioIndex(index === fallback ? undefined : index);
		},
		[current],
	);

	const selectSubtitle = useCallback(
		(index: number, at: number) => {
			if (!current) return;
			const next = subtitleStreams(current.source).find(
				(s) => s.Index === index,
			);
			saveSubtitlePref(next ?? null);
			const willBurn = next ? subtitleKind(next) === "image" : false;
			// Only a change in burn-in needs a new stream; plain text subtitles switch instantly.
			if (willBurn || burnIndex !== undefined) setResumeAt(at);
			setSubIndex(index);
		},
		[current, burnIndex],
	);

	if (item.isError || plan.isError) {
		const message =
			plan.error instanceof Error
				? plan.error.message
				: "This title couldn't be loaded.";
		return (
			<Backdrop>
				<div className="glass max-w-md rounded-card p-6">
					<p className="font-semibold">{message}</p>
					<button
						type="button"
						onClick={back}
						className="mt-4 cursor-pointer rounded-pill bg-ink px-6 py-2 text-sm font-semibold text-canvas"
					>
						Go back
					</button>
				</div>
			</Backdrop>
		);
	}

	if (!item.data || !current || subIndex === null) {
		return (
			<Backdrop>
				<div className="size-14 animate-spin rounded-full border-4 border-white/20 border-t-white" />
			</Backdrop>
		);
	}

	return (
		<Player
			key={`${id}:${current.url}`}
			item={item.data}
			plan={current}
			startSeconds={resumeAt ?? start}
			audioIndex={audioIndex}
			subIndex={subIndex}
			onBack={back}
			onSelectAudio={selectAudio}
			onSelectSubtitle={selectSubtitle}
			onDirectPlayFailed={(at) => {
				setResumeAt(at);
				setDirectOk(false);
			}}
		/>
	);
}

/** Full-screen player page, outside the normal nav shell. */
export function WatchPage() {
	return (
		<RequireAuth>
			<WatchInner />
		</RequireAuth>
	);
}
