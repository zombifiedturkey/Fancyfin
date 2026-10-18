import { useState } from "react";

type FadeImageProps = {
	src: string;
	alt?: string;
	className?: string;
	loading?: "lazy" | "eager";
};

/**
 * An image that fades in once it has loaded and removes itself if it fails, so whatever
 * is behind it (placeholder art) shows instead. Key it by `src` so the state resets when the picture changes.
 */
export function FadeImage({
	src,
	alt = "",
	className = "",
	loading = "lazy",
}: FadeImageProps) {
	const [state, setState] = useState<"loading" | "loaded" | "error">("loading");
	if (state === "error") return null;

	return (
		<img
			src={src}
			alt={alt}
			loading={loading}
			decoding="async"
			draggable={false}
			onLoad={() => setState("loaded")}
			onError={() => setState("error")}
			className={`transition-opacity duration-500 ease-out ${state === "loaded" ? "opacity-100" : "opacity-0"} ${className}`}
		/>
	);
}
