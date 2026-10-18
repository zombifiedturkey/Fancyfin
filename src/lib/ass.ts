import { type RefObject, useEffect } from "react";

/**
 * Draw an ASS/SSA subtitle file over the video with JASSUB, keeping the file's own styling,
 * positioning and effects. Pass null for no subtitles. The library is loaded only when needed.
 */
export function useAssSubtitles(
	videoRef: RefObject<HTMLVideoElement | null>,
	url: string | null,
) {
	useEffect(() => {
		const video = videoRef.current;
		if (!video || !url) return;

		let cancelled = false;
		let instance: { destroy: () => Promise<void> } | null = null;

		import("jassub")
			.then(({ default: JASSUB }) => {
				if (cancelled) return;
				instance = new JASSUB({ video, subUrl: url });
			})
			.catch(() => undefined);

		return () => {
			cancelled = true;
			void instance?.destroy().catch(() => undefined);
		};
	}, [videoRef, url]);
}
