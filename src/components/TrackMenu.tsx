import type { MediaSourceInfo } from "@jellyfin/sdk/lib/generated-client/models/media-source-info";
import { useEffect } from "react";
import {
	audioStreams,
	SCALE_VH,
	type SubtitleScale,
	streamLabel,
	subtitleKind,
	subtitleStreams,
} from "../lib/subtitles";

type TrackMenuProps = {
	source: MediaSourceInfo;
	audioIndex: number | undefined;
	subIndex: number;
	scale: SubtitleScale;
	onAudio: (index: number) => void;
	onSubtitle: (index: number) => void;
	onScale: (scale: SubtitleScale) => void;
	onClose: () => void;
};

function Option({
	active,
	onClick,
	children,
	hint,
}: {
	active: boolean;
	onClick: () => void;
	children: React.ReactNode;
	hint?: string;
}) {
	return (
		<li>
			<button
				type="button"
				onClick={onClick}
				className={`flex w-full cursor-pointer items-center gap-2 rounded-lg px-3 py-2 text-left text-sm transition-colors hover:bg-white/10 ${active ? "font-semibold text-ink" : "text-ink-muted"}`}
			>
				<span aria-hidden className="w-4 shrink-0 text-accent-strong">
					{active ? "✓" : ""}
				</span>
				<span className="min-w-0 flex-1 truncate">{children}</span>
				{hint && (
					<span className="shrink-0 text-xs text-ink-muted">{hint}</span>
				)}
			</button>
		</li>
	);
}

/** Glass popover for choosing the audio track, subtitle track and subtitle size. */
export function TrackMenu({
	source,
	audioIndex,
	subIndex,
	scale,
	onAudio,
	onSubtitle,
	onScale,
	onClose,
}: TrackMenuProps) {
	const audio = audioStreams(source);
	const subs = subtitleStreams(source);
	const currentAudio =
		audioIndex ?? source.DefaultAudioStreamIndex ?? audio[0]?.Index;

	useEffect(() => {
		const onKey = (event: KeyboardEvent) => {
			if (event.key === "Escape") {
				event.stopPropagation();
				onClose();
			}
		};
		window.addEventListener("keydown", onKey, true);
		return () => window.removeEventListener("keydown", onKey, true);
	}, [onClose]);

	const heading =
		"px-3 pb-1 text-xs font-medium uppercase tracking-[0.2em] text-ink-muted";

	return (
		<>
			<button
				type="button"
				aria-label="Close menu"
				onClick={onClose}
				className="fixed inset-0 z-10 cursor-default"
			/>
			<div className="glass absolute bottom-24 right-4 z-20 max-h-[60dvh] w-80 max-w-[calc(100%-2rem)] overflow-y-auto rounded-card p-3 sm:right-6">
				{audio.length > 0 && (
					<section>
						<h2 className={heading}>Audio</h2>
						<ul>
							{audio.map((stream, i) => (
								<Option
									key={stream.Index ?? i}
									active={stream.Index === currentAudio}
									onClick={() =>
										stream.Index !== undefined && onAudio(stream.Index)
									}
								>
									{streamLabel(stream, `Track ${i + 1}`)}
								</Option>
							))}
						</ul>
					</section>
				)}

				<section className={audio.length > 0 ? "mt-3" : ""}>
					<h2 className={heading}>Subtitles</h2>
					<ul>
						<Option active={subIndex < 0} onClick={() => onSubtitle(-1)}>
							Off
						</Option>
						{subs.map((stream, i) => (
							<Option
								key={stream.Index ?? i}
								active={stream.Index === subIndex}
								onClick={() =>
									stream.Index !== undefined && onSubtitle(stream.Index)
								}
								hint={
									subtitleKind(stream) === "image" ? "burned in" : undefined
								}
							>
								{streamLabel(stream, `Track ${i + 1}`)}
							</Option>
						))}
					</ul>
					{subs.length === 0 && (
						<p className="px-3 py-1 text-sm text-ink-muted">
							None in this file.
						</p>
					)}
				</section>

				<section className="mt-3">
					<h2 className={heading}>Text size</h2>
					<div className="flex gap-2 px-3">
						{(Object.keys(SCALE_VH) as SubtitleScale[]).map((key) => (
							<button
								key={key}
								type="button"
								aria-pressed={scale === key}
								onClick={() => onScale(key)}
								className={`flex-1 cursor-pointer rounded-pill py-1.5 text-sm capitalize transition-colors ${scale === key ? "bg-ink font-semibold text-canvas" : "bg-white/10 text-ink-muted hover:bg-white/20"}`}
							>
								{key}
							</button>
						))}
					</div>
				</section>
			</div>
		</>
	);
}
