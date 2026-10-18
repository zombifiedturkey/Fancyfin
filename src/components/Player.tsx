import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import { useNavigate } from "@tanstack/react-router";
import Hls from "hls.js";
import { useCallback, useEffect, useRef, useState } from "react";
import { useAssSubtitles } from "../lib/ass";
import { episodeLabel } from "../lib/format";
import {
	type PlaybackPlan,
	reportProgress,
	reportStart,
	reportStopped,
	useNextEpisode,
} from "../lib/playback";
import {
	loadSubtitleScale,
	SCALE_VH,
	type SubtitleScale,
	saveSubtitleScale,
	subtitleKind,
	subtitleStreams,
	subtitleUrl,
	useSilenceOtherTracks,
	useVttTrack,
} from "../lib/subtitles";
import { TrackMenu } from "./TrackMenu";

const HIDE_AFTER_MS = 3000;
const PROGRESS_EVERY_MS = 10_000;
const NEXT_COUNTDOWN_S = 8;

function clock(totalSeconds: number): string {
	if (!Number.isFinite(totalSeconds) || totalSeconds < 0) return "0:00";
	const s = Math.floor(totalSeconds % 60);
	const m = Math.floor((totalSeconds / 60) % 60);
	const h = Math.floor(totalSeconds / 3600);
	const pad = (n: number) => String(n).padStart(2, "0");
	return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`;
}

const round =
	"glass flex size-11 cursor-pointer items-center justify-center rounded-full text-ink transition-colors hover:bg-glass-strong";

function Icon({ children }: { children: React.ReactNode }) {
	return (
		<svg
			aria-hidden
			viewBox="0 0 24 24"
			className="size-5"
			fill="currentColor"
			stroke="currentColor"
			strokeWidth="1.5"
			strokeLinejoin="round"
			strokeLinecap="round"
		>
			{children}
		</svg>
	);
}

type PlayerProps = {
	item: BaseItemDto;
	plan: PlaybackPlan;
	/** Where to start, in seconds. */
	startSeconds: number;
	/** Chosen audio stream index, or undefined for the file's default. */
	audioIndex: number | undefined;
	/** Chosen subtitle stream index, or -1 for none. */
	subIndex: number;
	onBack: () => void;
	onSelectAudio: (index: number, atSeconds: number) => void;
	onSelectSubtitle: (index: number, atSeconds: number) => void;
	/** Called when the browser can't decode a direct play stream, so the page can ask for a transcode. */
	onDirectPlayFailed: (atSeconds: number) => void;
};

/** Full-screen video player with glass controls. */
export function Player({
	item,
	plan,
	startSeconds,
	audioIndex,
	subIndex,
	onBack,
	onSelectAudio,
	onSelectSubtitle,
	onDirectPlayFailed,
}: PlayerProps) {
	const navigate = useNavigate();
	const itemId = item.Id ?? "";
	const containerRef = useRef<HTMLDivElement>(null);
	const videoRef = useRef<HTMLVideoElement>(null);
	const hideTimer = useRef<number | undefined>(undefined);
	const tapTimer = useRef<number | undefined>(undefined);
	const lastTap = useRef<number | null>(null);
	const lastWasTouch = useRef(false);

	const [playing, setPlaying] = useState(false);
	const [buffering, setBuffering] = useState(true);
	const [time, setTime] = useState(0);
	const [duration, setDuration] = useState(
		item.RunTimeTicks ? item.RunTimeTicks / 10_000_000 : 0,
	);
	const [bufferedEnd, setBufferedEnd] = useState(0);
	const [volume, setVolume] = useState(1);
	const [muted, setMuted] = useState(false);
	const [fullscreen, setFullscreen] = useState(false);
	const [controlsVisible, setControlsVisible] = useState(true);
	const [error, setError] = useState<string | null>(null);
	const [ended, setEnded] = useState(false);
	const [countdown, setCountdown] = useState(NEXT_COUNTDOWN_S);
	const [cancelledNext, setCancelledNext] = useState(false);

	const nextEpisode = useNextEpisode(item).data ?? null;

	const [menuOpen, setMenuOpen] = useState(false);
	const [scale, setScale] = useState<SubtitleScale>(loadSubtitleScale);

	// Text subtitles are drawn here; picture subtitles were burned into the stream by the server.
	const chosenSub =
		subIndex >= 0
			? subtitleStreams(plan.source).find((s) => s.Index === subIndex)
			: undefined;
	const subKind = chosenSub ? subtitleKind(chosenSub) : null;
	const sourceId = plan.source.Id;
	const vttUrl =
		subKind === "vtt" && sourceId
			? subtitleUrl(itemId, sourceId, subIndex, "vtt")
			: null;
	useSilenceOtherTracks(videoRef, `${plan.url}|${subIndex}`);
	useVttTrack(videoRef, vttUrl);
	const assUrl =
		subKind === "ass" && sourceId
			? subtitleUrl(itemId, sourceId, subIndex, "ass")
			: null;
	useAssSubtitles(videoRef, assUrl);

	// What the progress reporter needs, kept in a ref so the interval never goes stale.
	const live = useRef({
		pos: startSeconds,
		paused: true,
		muted: false,
		volume: 1,
	});

	// Attach the stream.
	useEffect(() => {
		const video = videoRef.current;
		if (!video) return;
		let hls: Hls | null = null;
		setError(null);
		setBuffering(true);

		if (plan.hls && Hls.isSupported()) {
			hls = new Hls({ startPosition: startSeconds > 1 ? startSeconds : -1 });
			hls.loadSource(plan.url);
			hls.attachMedia(video);
			hls.on(Hls.Events.ERROR, (_event, data) => {
				if (!data.fatal) return;
				if (data.type === Hls.ErrorTypes.NETWORK_ERROR) hls?.startLoad();
				else if (data.type === Hls.ErrorTypes.MEDIA_ERROR)
					hls?.recoverMediaError();
				else
					setError(
						"Playback failed. The server couldn't transcode this title.",
					);
			});
			hls.on(Hls.Events.MANIFEST_PARSED, () => {
				void video.play().catch(() => setPlaying(false));
			});
		} else {
			video.src = plan.url;
			const seek = () => {
				if (startSeconds > 1) video.currentTime = startSeconds;
			};
			video.addEventListener("loadedmetadata", seek, { once: true });
			void video.play().catch(() => setPlaying(false));
		}

		return () => {
			hls?.destroy();
			video.removeAttribute("src");
			video.load();
		};
	}, [plan.url]);

	// Tell the server what we're doing.
	useEffect(() => {
		let started = false;
		const info = () => ({
			itemId,
			plan,
			positionSeconds: live.current.pos,
			paused: live.current.paused,
			muted: live.current.muted,
			volume: live.current.volume,
		});
		const video = videoRef.current;
		const onFirstPlay = () => {
			if (started) return;
			started = true;
			void reportStart(info());
		};
		video?.addEventListener("playing", onFirstPlay);
		const timer = window.setInterval(() => {
			if (started) void reportProgress(info());
		}, PROGRESS_EVERY_MS);
		return () => {
			video?.removeEventListener("playing", onFirstPlay);
			window.clearInterval(timer);
			if (started) void reportStopped(info());
		};
	}, [itemId, plan]);

	const togglePlay = useCallback(() => {
		const video = videoRef.current;
		if (!video) return;
		if (video.paused) void video.play().catch(() => undefined);
		else video.pause();
	}, []);

	const seekBy = useCallback((delta: number) => {
		const video = videoRef.current;
		if (!video) return;
		video.currentTime = Math.max(
			0,
			Math.min(
				video.duration || Number.MAX_SAFE_INTEGER,
				video.currentTime + delta,
			),
		);
	}, []);

	const toggleFullscreen = useCallback(() => {
		if (document.fullscreenElement) void document.exitFullscreen();
		else void containerRef.current?.requestFullscreen?.();
	}, []);

	const toggleMute = useCallback(() => {
		const video = videoRef.current;
		if (video) video.muted = !video.muted;
	}, []);

	const changeVolume = useCallback((delta: number) => {
		const video = videoRef.current;
		if (!video) return;
		video.muted = false;
		video.volume = Math.max(0, Math.min(1, video.volume + delta));
	}, []);

	const poke = useCallback(() => {
		setControlsVisible(true);
		window.clearTimeout(hideTimer.current);
		hideTimer.current = window.setTimeout(() => {
			if (videoRef.current && !videoRef.current.paused)
				setControlsVisible(false);
		}, HIDE_AFTER_MS);
	}, []);

	useEffect(() => {
		const onFullscreen = () =>
			setFullscreen(Boolean(document.fullscreenElement));
		document.addEventListener("fullscreenchange", onFullscreen);
		return () => {
			document.removeEventListener("fullscreenchange", onFullscreen);
			window.clearTimeout(hideTimer.current);
			window.clearTimeout(tapTimer.current);
		};
	}, []);

	// Keyboard shortcuts.
	useEffect(() => {
		const onKey = (event: KeyboardEvent) => {
			const target = event.target as HTMLElement | null;
			if (
				target &&
				/^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName) &&
				target.getAttribute("type") !== "range"
			)
				return;
			if (event.metaKey || event.ctrlKey || event.altKey) return;
			switch (event.key) {
				case " ":
				case "k":
					if (target?.tagName === "BUTTON") return;
					event.preventDefault();
					togglePlay();
					break;
				case "ArrowLeft":
				case "j":
					event.preventDefault();
					seekBy(event.key === "j" ? -10 : -5);
					break;
				case "ArrowRight":
				case "l":
					event.preventDefault();
					seekBy(event.key === "l" ? 10 : 5);
					break;
				case "ArrowUp":
					event.preventDefault();
					changeVolume(0.05);
					break;
				case "ArrowDown":
					event.preventDefault();
					changeVolume(-0.05);
					break;
				case "m":
					toggleMute();
					break;
				case "f":
					toggleFullscreen();
					break;
				default:
					return;
			}
			poke();
		};
		window.addEventListener("keydown", onKey);
		return () => window.removeEventListener("keydown", onKey);
	}, [togglePlay, seekBy, changeVolume, toggleMute, toggleFullscreen, poke]);

	// Up-next countdown after the video ends.
	const showNext = ended && nextEpisode && !cancelledNext;
	const nextId = nextEpisode?.Id;
	useEffect(() => {
		if (!showNext || !nextId) return;
		setCountdown(NEXT_COUNTDOWN_S);
		const timer = window.setInterval(() => {
			setCountdown((n) => {
				if (n <= 1) {
					window.clearInterval(timer);
					void navigate({
						to: "/watch/$id",
						params: { id: nextId },
						search: { start: 0 },
						replace: true,
					});
					return 0;
				}
				return n - 1;
			});
		}, 1000);
		return () => window.clearInterval(timer);
	}, [showNext, nextId, navigate]);

	const title =
		item.Type === "Episode"
			? (item.SeriesName ?? item.Name ?? "")
			: (item.Name ?? "");
	const subtitle =
		item.Type === "Episode"
			? [episodeLabel(item), item.Name].filter(Boolean).join(" · ")
			: "";

	const syncBuffered = () => {
		const video = videoRef.current;
		if (!video) return;
		const ranges = video.buffered;
		let end = 0;
		for (let i = 0; i < ranges.length; i++) {
			if (
				ranges.start(i) <= video.currentTime &&
				ranges.end(i) >= video.currentTime
			)
				end = ranges.end(i);
		}
		setBufferedEnd(end);
	};

	const visible = controlsVisible || !playing || Boolean(error) || menuOpen;
	const pct = duration > 0 ? (time / duration) * 100 : 0;
	const bufPct = duration > 0 ? (bufferedEnd / duration) * 100 : 0;

	return (
		<div
			ref={containerRef}
			role="application"
			aria-label={`Playing ${title}`}
			onMouseMove={poke}
			className={`fixed inset-0 z-50 select-none bg-black ${visible ? "" : "cursor-none"}`}
		>
			{/* biome-ignore lint/a11y/useMediaCaption: subtitles arrive in the next stage */}
			<video
				ref={videoRef}
				className="ff-video size-full object-contain"
				style={{ "--ff-sub-size": SCALE_VH[scale] } as React.CSSProperties}
				playsInline
				onClick={(event) => {
					const touch =
						event.nativeEvent instanceof PointerEvent &&
						event.nativeEvent.pointerType === "touch";
					lastWasTouch.current = touch;
					// Mouse: click plays or pauses.
					if (!touch) {
						togglePlay();
						return;
					}
					// Touch: tap shows or hides the controls; double-tap the sides to skip, the middle to play or pause.
					const now = Date.now();
					const rect = event.currentTarget.getBoundingClientRect();
					const x = (event.clientX - rect.left) / rect.width;
					if (lastTap.current && now - lastTap.current < 300) {
						window.clearTimeout(tapTimer.current);
						lastTap.current = null;
						if (x < 0.35) seekBy(-10);
						else if (x > 0.65) seekBy(10);
						else togglePlay();
						poke();
						return;
					}
					lastTap.current = now;
					const wasVisible = visible;
					tapTimer.current = window.setTimeout(() => {
						lastTap.current = null;
						if (wasVisible && playing) setControlsVisible(false);
						else poke();
					}, 300);
				}}
				onDoubleClick={() => {
					if (!lastWasTouch.current) toggleFullscreen();
				}}
				onPlay={() => {
					setPlaying(true);
					live.current.paused = false;
					poke();
				}}
				onPause={() => {
					setPlaying(false);
					live.current.paused = true;
					setControlsVisible(true);
				}}
				onPlaying={() => setBuffering(false)}
				onWaiting={() => setBuffering(true)}
				onCanPlay={() => setBuffering(false)}
				onTimeUpdate={(event) => {
					const t = event.currentTarget.currentTime;
					live.current.pos = t;
					setTime(t);
					syncBuffered();
				}}
				onProgress={syncBuffered}
				onDurationChange={(event) => {
					const d = event.currentTarget.duration;
					if (Number.isFinite(d) && d > 0) setDuration(d);
				}}
				onVolumeChange={(event) => {
					const v = event.currentTarget;
					setVolume(v.volume);
					setMuted(v.muted);
					live.current.volume = v.volume;
					live.current.muted = v.muted;
				}}
				onEnded={() => setEnded(true)}
				onError={() => {
					if (plan.method === "DirectPlay")
						onDirectPlayFailed(live.current.pos);
					else if (!plan.hls || !Hls.isSupported())
						setError("Playback failed.");
				}}
			/>

			{buffering && !error && (
				<div className="pointer-events-none absolute inset-0 flex items-center justify-center">
					<div className="size-14 animate-spin rounded-full border-4 border-white/20 border-t-white" />
				</div>
			)}

			{error && (
				<div className="absolute inset-0 flex items-center justify-center p-6">
					<div className="glass max-w-md rounded-card p-6 text-center">
						<p className="font-semibold">{error}</p>
						<button
							type="button"
							onClick={onBack}
							className="mt-4 cursor-pointer rounded-pill bg-ink px-6 py-2 text-sm font-semibold text-canvas"
						>
							Go back
						</button>
					</div>
				</div>
			)}

			{/* Top bar */}
			<div
				className={`absolute inset-x-0 top-0 flex items-center gap-4 bg-linear-to-b from-black/70 to-transparent p-4 transition-opacity duration-300 sm:p-6 ${visible ? "opacity-100" : "pointer-events-none opacity-0"}`}
			>
				<button
					type="button"
					aria-label="Back"
					onClick={onBack}
					className={round}
				>
					<Icon>
						<path d="M15 5l-7 7 7 7" fill="none" strokeWidth="2.5" />
					</Icon>
				</button>
				<div className="min-w-0">
					<p className="truncate text-lg font-semibold">{title}</p>
					{subtitle && (
						<p className="truncate text-sm text-ink-muted">{subtitle}</p>
					)}
				</div>
				<span className="glass ml-auto shrink-0 rounded-pill px-3 py-1 text-xs font-medium text-ink-muted">
					{plan.method === "DirectPlay" ? "Direct play" : "Transcoding"}
				</span>
			</div>

			{/* Bottom controls */}
			<div
				className={`absolute inset-x-0 bottom-0 bg-linear-to-t from-black/80 to-transparent p-4 pb-[max(1rem,env(safe-area-inset-bottom))] pt-16 transition-opacity duration-300 sm:p-6 sm:pt-20 ${visible ? "opacity-100" : "pointer-events-none opacity-0"}`}
			>
				<div className="flex items-center gap-3">
					<span className="w-14 text-right text-sm tabular-nums text-ink-muted">
						{clock(time)}
					</span>
					<div className="group relative h-6 flex-1">
						<div className="absolute inset-x-0 top-1/2 h-1.5 -translate-y-1/2 overflow-hidden rounded-pill bg-white/20 transition-all group-hover:h-2">
							<div
								className="absolute inset-y-0 left-0 bg-white/30"
								style={{ width: `${bufPct}%` }}
							/>
							<div
								className="absolute inset-y-0 left-0 bg-white"
								style={{ width: `${pct}%` }}
							/>
						</div>
						<input
							type="range"
							aria-label="Seek"
							min={0}
							max={duration || 1}
							step={1}
							value={Math.min(time, duration || 1)}
							onChange={(event) => {
								const video = videoRef.current;
								if (video) video.currentTime = Number(event.target.value);
							}}
							className="absolute inset-0 size-full cursor-pointer opacity-0"
						/>
					</div>
					<span className="w-14 text-sm tabular-nums text-ink-muted">
						{clock(duration)}
					</span>
				</div>

				<div className="mt-3 flex items-center gap-3">
					<button
						type="button"
						aria-label={playing ? "Pause" : "Play"}
						onClick={togglePlay}
						className={round}
					>
						<Icon>
							{playing ? (
								<path d="M7 5v14M17 5v14" strokeWidth="3.5" />
							) : (
								<path d="M8 5l11 7-11 7z" />
							)}
						</Icon>
					</button>
					<button
						type="button"
						aria-label="Back 10 seconds"
						onClick={() => seekBy(-10)}
						className={round}
					>
						<span className="text-xs font-bold">−10</span>
					</button>
					<button
						type="button"
						aria-label="Forward 10 seconds"
						onClick={() => seekBy(10)}
						className={round}
					>
						<span className="text-xs font-bold">+10</span>
					</button>

					<div className="group/vol flex items-center gap-2">
						<button
							type="button"
							aria-label={muted ? "Unmute" : "Mute"}
							onClick={toggleMute}
							className={round}
						>
							<Icon>
								<path d="M4 9v6h4l5 4V5L8 9z" />
								{!muted && volume > 0 && (
									<path d="M16 9a4 4 0 010 6" fill="none" strokeWidth="2" />
								)}
								{(muted || volume === 0) && (
									<path d="M16 9l5 6M21 9l-5 6" fill="none" strokeWidth="2" />
								)}
							</Icon>
						</button>
						<input
							type="range"
							aria-label="Volume"
							min={0}
							max={1}
							step={0.05}
							value={muted ? 0 : volume}
							onChange={(event) => {
								const video = videoRef.current;
								if (!video) return;
								video.muted = false;
								video.volume = Number(event.target.value);
							}}
							className="hidden w-24 cursor-pointer accent-white sm:block"
						/>
					</div>

					<button
						type="button"
						aria-label="Audio and subtitles"
						aria-expanded={menuOpen}
						onClick={() => setMenuOpen((open) => !open)}
						className={`${round} ml-auto`}
					>
						<Icon>
							<rect
								x="3"
								y="5"
								width="18"
								height="14"
								rx="3"
								fill="none"
								strokeWidth="2"
							/>
							<path
								d="M7 11h3M13 11h4M7 15h5M15 15h2"
								fill="none"
								strokeWidth="2"
							/>
						</Icon>
					</button>
					<button
						type="button"
						aria-label={fullscreen ? "Exit full screen" : "Full screen"}
						onClick={toggleFullscreen}
						className={round}
					>
						<Icon>
							{fullscreen ? (
								<path
									d="M9 4v5H4M15 4v5h5M9 20v-5H4M15 20v-5h5"
									fill="none"
									strokeWidth="2"
								/>
							) : (
								<path
									d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"
									fill="none"
									strokeWidth="2"
								/>
							)}
						</Icon>
					</button>
				</div>
			</div>

			<style>{`.ff-video::cue { font-size: var(--ff-sub-size); line-height: 1.25; background: rgba(0,0,0,0.6); color: #fff; }`}</style>

			{menuOpen && (
				<TrackMenu
					source={plan.source}
					audioIndex={audioIndex}
					subIndex={subIndex}
					scale={scale}
					onAudio={(index) => {
						setMenuOpen(false);
						onSelectAudio(
							index,
							videoRef.current?.currentTime ?? live.current.pos,
						);
					}}
					onSubtitle={(index) => {
						setMenuOpen(false);
						onSelectSubtitle(
							index,
							videoRef.current?.currentTime ?? live.current.pos,
						);
					}}
					onScale={(next) => {
						setScale(next);
						saveSubtitleScale(next);
					}}
					onClose={() => setMenuOpen(false)}
				/>
			)}

			{/* Up next */}
			{showNext && nextEpisode && (
				<div className="glass absolute bottom-28 right-4 w-80 max-w-[calc(100%-2rem)] rounded-card p-5 sm:right-6">
					<p className="text-xs font-medium uppercase tracking-[0.2em] text-accent-strong">
						Up next · {countdown}s
					</p>
					<p className="mt-2 line-clamp-2 font-semibold">{nextEpisode.Name}</p>
					<p className="text-sm text-ink-muted">{episodeLabel(nextEpisode)}</p>
					<div className="mt-4 flex gap-2">
						<button
							type="button"
							onClick={() =>
								nextId &&
								void navigate({
									to: "/watch/$id",
									params: { id: nextId },
									search: { start: 0 },
									replace: true,
								})
							}
							className="cursor-pointer rounded-pill bg-ink px-5 py-2 text-sm font-semibold text-canvas"
						>
							Play now
						</button>
						<button
							type="button"
							onClick={() => setCancelledNext(true)}
							className="glass cursor-pointer rounded-pill px-5 py-2 text-sm font-semibold"
						>
							Cancel
						</button>
					</div>
				</div>
			)}
		</div>
	);
}
