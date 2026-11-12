/**
 * Pop-out player support. Chromium browsers (Chrome, Edge) can open a small always-on-top,
 * resizable window that holds real page content (Document Picture-in-Picture). Fancyfin moves
 * the whole player into it, so the glass controls and subtitles come with it.
 */

type DocumentPip = {
	requestWindow(options?: {
		width?: number;
		height?: number;
		disallowReturnToOpener?: boolean;
	}): Promise<Window>;
};

export const POP_OUT_WIDTH = 480;
const FALLBACK_HEIGHT = 270;
const MIN_HEIGHT = 160;
const MAX_HEIGHT = 520;

function docPip(): DocumentPip | undefined {
	return (window as unknown as { documentPictureInPicture?: DocumentPip })
		.documentPictureInPicture;
}

/** True when this browser can open the floating player window. */
export function popOutSupported(): boolean {
	return typeof window !== "undefined" && Boolean(docPip());
}

/** Window size that fits the video's shape, so there are no black bars to start with. */
export function popOutSize(
	videoWidth: number,
	videoHeight: number,
	width = POP_OUT_WIDTH,
): { width: number; height: number } {
	if (!(videoWidth > 0) || !(videoHeight > 0))
		return { width, height: FALLBACK_HEIGHT };
	const height = Math.round((width * videoHeight) / videoWidth);
	return {
		width,
		height: Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, height)),
	};
}

/** Copy the page's styles and theme settings into the floating window. */
function copyAppearance(from: Document, to: Document) {
	for (const sheet of Array.from(from.styleSheets)) {
		if (sheet.href) {
			const link = to.createElement("link");
			link.rel = "stylesheet";
			link.href = sheet.href;
			to.head.append(link);
			continue;
		}
		try {
			const style = to.createElement("style");
			style.textContent = Array.from(sheet.cssRules)
				.map((rule) => rule.cssText)
				.join("\n");
			to.head.append(style);
		} catch {
			// A sheet we aren't allowed to read; skip it.
		}
	}
	const source = from.documentElement;
	const target = to.documentElement;
	target.className = source.className;
	target.setAttribute("style", source.getAttribute("style") ?? "");
	for (const name of source.getAttributeNames()) {
		if (name.startsWith("data-")) {
			target.setAttribute(name, source.getAttribute(name) ?? "");
		}
	}
	to.body.style.margin = "0";
	to.body.style.background = "#000";
	to.body.style.overflow = "hidden";
}

/** Ask the browser for the floating window, sized for the video. Needs a click or key press. */
export async function requestPopOutWindow(
	videoWidth: number,
	videoHeight: number,
): Promise<Window> {
	const api = docPip();
	if (!api) throw new Error("Pop-out isn't supported in this browser.");
	const win = await api.requestWindow(popOutSize(videoWidth, videoHeight));
	copyAppearance(document, win.document);
	return win;
}
