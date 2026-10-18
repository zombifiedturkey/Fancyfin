import type { MediaSourceInfo } from "@jellyfin/sdk/lib/generated-client/models/media-source-info";
import type { MediaStream } from "@jellyfin/sdk/lib/generated-client/models/media-stream";
import { type RefObject, useEffect } from "react";
import { apiUrl } from "./api-base";
import { api } from "./jellyfin";

/** How a subtitle track is shown: plain text (WebVTT), styled ASS/SSA, or a picture format that must be burned in. */
export type SubtitleKind = "vtt" | "ass" | "image";

export function subtitleKind(stream: MediaStream): SubtitleKind {
	if (stream.IsTextSubtitleStream === false) return "image";
	const codec = (stream.Codec ?? "").toLowerCase();
	return codec === "ass" || codec === "ssa" ? "ass" : "vtt";
}

export function audioStreams(source: MediaSourceInfo): MediaStream[] {
	return (source.MediaStreams ?? []).filter((s) => s.Type === "Audio");
}

export function subtitleStreams(source: MediaSourceInfo): MediaStream[] {
	return (source.MediaStreams ?? []).filter((s) => s.Type === "Subtitle");
}

export function streamLabel(stream: MediaStream, fallback: string): string {
	return stream.DisplayTitle || stream.Title || stream.Language || fallback;
}

/** Server-converted text subtitle file for a stream (the server turns SRT and friends into VTT). */
export function subtitleUrl(
	itemId: string,
	sourceId: string,
	index: number,
	format: "vtt" | "ass",
): string {
	const key = encodeURIComponent(api.accessToken);
	return apiUrl(
		`/Videos/${itemId}/${sourceId}/Subtitles/${index}/0/Stream.${format}?api_key=${key}`,
	);
}

// ---- Remembered choices ----------------------------------------------------

const LANG_KEY = "fancyfin.subtitleLang";
const SCALE_KEY = "fancyfin.subtitleScale";

export type SubtitleScale = "small" | "medium" | "large";
/** Cue text height as a share of the screen height. */
export const SCALE_VH: Record<SubtitleScale, string> = {
	small: "3.2vh",
	medium: "4.2vh",
	large: "5.6vh",
};

/** "off", a language code like "eng", or null when the user has never chosen. */
export function loadSubtitlePref(): string | null {
	try {
		return localStorage.getItem(LANG_KEY);
	} catch {
		return null;
	}
}

export function saveSubtitlePref(stream: MediaStream | null) {
	try {
		localStorage.setItem(LANG_KEY, stream?.Language ?? "off");
	} catch {
		// ignore
	}
}

export function loadSubtitleScale(): SubtitleScale {
	try {
		const value = localStorage.getItem(SCALE_KEY);
		if (value === "small" || value === "medium" || value === "large")
			return value;
	} catch {
		// ignore
	}
	return "medium";
}

export function saveSubtitleScale(scale: SubtitleScale) {
	try {
		localStorage.setItem(SCALE_KEY, scale);
	} catch {
		// ignore
	}
}

/** Which subtitle to start with: the language the user last chose, else none. Returns a stream index or -1. */
export function defaultSubtitleIndex(source: MediaSourceInfo): number {
	const pref = loadSubtitlePref();
	if (!pref || pref === "off") return -1;
	const match = subtitleStreams(source).find(
		(s) => s.Language === pref && !s.IsForced,
	);
	const any = match ?? subtitleStreams(source).find((s) => s.Language === pref);
	return any?.Index ?? -1;
}

// ---- Native tracks -----------------------------------------------------------

/** Text tracks we created ourselves; every other track on the video is left switched off. */
const ownTracks = new WeakSet<TextTrack>();

/**
 * Browsers can show subtitle tracks found inside the file or stream on their own, which draws a second
 * copy over ours. Keep every track that isn't ours disabled.
 */
export function useSilenceOtherTracks(
	videoRef: RefObject<HTMLVideoElement | null>,
	key: unknown,
) {
	useEffect(() => {
		const video = videoRef.current;
		if (!video) return;
		const silence = () => {
			for (const track of Array.from(video.textTracks)) {
				if (!ownTracks.has(track) && track.mode !== "disabled")
					track.mode = "disabled";
			}
		};
		silence();
		video.textTracks.addEventListener("addtrack", silence);
		video.textTracks.addEventListener("change", silence);
		return () => {
			video.textTracks.removeEventListener("addtrack", silence);
			video.textTracks.removeEventListener("change", silence);
		};
	}, [videoRef, key]);
}

// ---- WebVTT rendering --------------------------------------------------------

/** Show a WebVTT file on the video through a native text track. Pass null for no subtitles. */
export function useVttTrack(
	videoRef: RefObject<HTMLVideoElement | null>,
	url: string | null,
) {
	useEffect(() => {
		const video = videoRef.current;
		if (!video || !url) return;
		let cancelled = false;
		let objectUrl: string | null = null;
		let element: HTMLTrackElement | null = null;

		fetch(url)
			.then((response) =>
				response.ok
					? response.blob()
					: Promise.reject(new Error("no subtitles")),
			)
			.then((blob) => {
				if (cancelled) return;
				objectUrl = URL.createObjectURL(blob);
				element = document.createElement("track");
				element.kind = "subtitles";
				ownTracks.add(element.track);
				element.default = true;
				element.src = objectUrl;
				video.appendChild(element);
				element.track.mode = "showing";
			})
			.catch(() => undefined);

		return () => {
			cancelled = true;
			element?.remove();
			if (objectUrl) URL.revokeObjectURL(objectUrl);
		};
	}, [videoRef, url]);
}
