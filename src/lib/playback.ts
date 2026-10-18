import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import type { MediaSourceInfo } from "@jellyfin/sdk/lib/generated-client/models/media-source-info";
import type { PlayMethod } from "@jellyfin/sdk/lib/generated-client/models/play-method";
import { getMediaInfoApi } from "@jellyfin/sdk/lib/utils/api/media-info-api";
import { getSessionApi } from "@jellyfin/sdk/lib/utils/api/session-api";
import { getShowApi } from "@jellyfin/sdk/lib/utils/api/show-api";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useSession } from "../auth/AuthProvider";
import { apiUrl } from "./api-base";
import { buildDeviceProfile } from "./device-profile";
import { episodeLabel } from "./format";
import { api } from "./jellyfin";

export type PlaybackPlan = {
	itemId: string;
	method: PlayMethod;
	/** Same-origin URL to hand to the player. */
	url: string;
	/** True when `url` is an HLS playlist. */
	hls: boolean;
	source: MediaSourceInfo;
	playSessionId: string;
};

function withApiKey(url: string): string {
	if (/[?&]api_key=/i.test(url)) return url;
	return `${url}${url.includes("?") ? "&" : "?"}api_key=${encodeURIComponent(api.accessToken)}`;
}

/** Ask the server how to play this item and turn the answer into a URL. */
export type PlanOptions = {
	allowDirect: boolean;
	/** Chosen audio track, or undefined for the file's default. */
	audioIndex?: number;
	/** Picture subtitle to burn into the video, or undefined for none. */
	burnIndex?: number;
};

async function fetchPlan(
	itemId: string,
	userId: string,
	{ allowDirect, audioIndex, burnIndex }: PlanOptions,
): Promise<PlaybackPlan> {
	const response = await getMediaInfoApi(api).getPostedPlaybackInfo({
		itemId,
		playbackInfoDto: {
			UserId: userId,
			DeviceProfile: buildDeviceProfile(),
			MaxStreamingBitrate: 120_000_000,
			EnableDirectPlay: allowDirect,
			EnableDirectStream: allowDirect,
			EnableTranscoding: true,
			AllowVideoStreamCopy: true,
			AllowAudioStreamCopy: true,
			AutoOpenLiveStream: true,
			AudioStreamIndex: audioIndex,
			SubtitleStreamIndex: burnIndex ?? -1,
			AlwaysBurnInSubtitleWhenTranscoding: burnIndex !== undefined,
		},
	});

	const info = response.data;
	if (info.ErrorCode)
		throw new Error(`The server can't play this item (${info.ErrorCode}).`);
	const source = info.MediaSources?.[0];
	if (!source?.Id)
		throw new Error("The server returned no playable version of this item.");
	const playSessionId = info.PlaySessionId ?? crypto.randomUUID();

	if (
		allowDirect &&
		(source.SupportsDirectPlay || source.SupportsDirectStream)
	) {
		const params = new URLSearchParams({
			static: "true",
			MediaSourceId: source.Id,
			DeviceId: api.deviceInfo.id,
			PlaySessionId: playSessionId,
			api_key: api.accessToken,
		});
		if (source.ETag) params.set("Tag", source.ETag);
		return {
			method: "DirectPlay",
			url: apiUrl(`/Videos/${itemId}/stream?${params.toString()}`),
			hls: false,
			source,
			playSessionId,
			itemId,
		};
	}

	if (source.TranscodingUrl) {
		return {
			method: "Transcode",
			url: withApiKey(apiUrl(source.TranscodingUrl)),
			hls: true,
			source,
			playSessionId,
			itemId,
		};
	}

	throw new Error(
		"This item can't be played in a browser, and the server couldn't transcode it.",
	);
}

/** The plan for playing an item. Changing the audio track or burning in subtitles means a fresh plan. */
export function usePlaybackPlan(itemId: string, options: PlanOptions) {
	const { userId } = useSession();
	return useQuery({
		queryKey: [
			"playback-plan",
			itemId,
			options.allowDirect,
			options.audioIndex ?? null,
			options.burnIndex ?? null,
		],
		queryFn: () => fetchPlan(itemId, userId, options),
		// Keep the current picture up while a new plan loads, so changing track doesn't flash black.
		placeholderData: keepPreviousData,
		staleTime: Number.POSITIVE_INFINITY,
		gcTime: 0,
		retry: false,
		refetchOnWindowFocus: false,
	});
}

/** What the next episode is, if the item is an episode with something after it. */
export function useNextEpisode(item: BaseItemDto | undefined) {
	const { userId } = useSession();
	const seriesId = item?.SeriesId;
	const itemId = item?.Id;
	return useQuery({
		queryKey: ["next-episode", itemId],
		enabled: Boolean(seriesId && itemId && item?.Type === "Episode"),
		queryFn: async () => {
			if (!seriesId || !itemId) return null;
			const response = await getShowApi(api).getEpisodes({
				seriesId,
				userId,
				startItemId: itemId,
				limit: 2,
			});
			const items = response.data.Items ?? [];
			return items.find((candidate) => candidate.Id !== itemId) ?? null;
		},
	});
}

const TICKS_PER_SECOND = 10_000_000;
export const secondsToTicks = (seconds: number) =>
	Math.round(seconds * TICKS_PER_SECOND);

type ReportInfo = {
	itemId: string;
	plan: PlaybackPlan;
	positionSeconds: number;
	paused: boolean;
	muted: boolean;
	volume: number;
};

function body({
	itemId,
	plan,
	positionSeconds,
	paused,
	muted,
	volume,
}: ReportInfo) {
	return {
		ItemId: itemId,
		MediaSourceId: plan.source.Id,
		PlaySessionId: plan.playSessionId,
		PlayMethod: plan.method,
		PositionTicks: secondsToTicks(positionSeconds),
		IsPaused: paused,
		IsMuted: muted,
		VolumeLevel: Math.round(volume * 100),
		CanSeek: true,
	};
}

/** Progress reports are best effort: a failed one must never interrupt playback. */
export async function reportStart(info: ReportInfo) {
	try {
		await getSessionApi(api).reportPlaybackStart({
			playbackStartInfo: body(info),
		});
	} catch {
		// ignore
	}
}

export async function reportProgress(info: ReportInfo) {
	try {
		await getSessionApi(api).reportPlaybackProgress({
			playbackProgressInfo: body(info),
		});
	} catch {
		// ignore
	}
}

export async function reportStopped(info: ReportInfo) {
	try {
		await getSessionApi(api).reportPlaybackStopped({
			playbackStopInfo: {
				ItemId: info.itemId,
				MediaSourceId: info.plan.source.Id,
				PlaySessionId: info.plan.playSessionId,
				PositionTicks: secondsToTicks(info.positionSeconds),
			},
		});
	} catch {
		// ignore
	}
}

export type PlayTarget = {
	/** The item to hand to the player: the title itself, or for a series its next episode. */
	id: string;
	/** Where to start, in seconds (0 when there's nothing to resume). */
	start: number;
	/** For series: which episode "Play" will start, e.g. "S1:E3". */
	label?: string;
};

function resumeSeconds(item: BaseItemDto): number {
	const ticks = item.UserData?.PlaybackPositionTicks ?? 0;
	const total = item.RunTimeTicks ?? 0;
	if (ticks <= 0) return 0;
	// Within the last 5% counts as finished, so start from the top.
	if (total > 0 && ticks / total > 0.95) return 0;
	return ticks / TICKS_PER_SECOND;
}

/** What the Play button should open. Movies and episodes play themselves; a series plays its next-up episode. */
export function usePlayTarget(
	item: BaseItemDto,
): PlayTarget | null | undefined {
	const { userId } = useSession();
	const isSeries = item.Type === "Series";
	const seriesId = item.Id;

	const episode = useQuery({
		queryKey: ["play-target", userId, seriesId],
		enabled: isSeries && Boolean(seriesId),
		queryFn: async () => {
			if (!seriesId) return null;
			const next = await getShowApi(api).getNextUp({
				userId,
				seriesId,
				limit: 1,
			});
			const first = next.data.Items?.[0];
			if (first) return first;
			const all = await getShowApi(api).getEpisodes({
				seriesId,
				userId,
				limit: 1,
			});
			return all.data.Items?.[0] ?? null;
		},
	});

	if (!isSeries) {
		return item.Id ? { id: item.Id, start: resumeSeconds(item) } : null;
	}
	if (episode.isPending) return undefined;
	const target = episode.data;
	if (!target?.Id) return null;
	return {
		id: target.Id,
		start: resumeSeconds(target),
		label: episodeLabel(target),
	};
}
