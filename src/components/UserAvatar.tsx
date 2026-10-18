import { useState } from "react";
import { apiUrl } from "../lib/api-base";
import { hueFor } from "../lib/hue";

type UserAvatarProps = {
	userId: string;
	name: string;
	/** The user's PrimaryImageTag. Without one the user has no picture and initials are shown. */
	imageTag?: string | null;
	/** Size and text size, e.g. "size-10 text-sm". */
	className?: string;
};

function initialsFor(name: string): string {
	const parts = name.trim().split(/\s+/).filter(Boolean);
	const first = parts[0]?.[0] ?? "?";
	const last = parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? "") : "";
	return (first + last).toUpperCase();
}

/** Newer servers serve user pictures from /UserImage, older ones from /Users/{id}/Images. Try both. */
function imageSources(userId: string, imageTag: string): string[] {
	const tag = encodeURIComponent(imageTag);
	return [
		apiUrl(`/UserImage?userId=${userId}&tag=${tag}&maxHeight=256`),
		apiUrl(`/Users/${userId}/Images/Primary?tag=${tag}&maxHeight=256`),
	];
}

function AvatarInner({
	userId,
	name,
	imageTag,
	className = "size-10 text-sm",
}: UserAvatarProps) {
	const [failedCount, setFailedCount] = useState(0);
	const sources = imageTag ? imageSources(userId, imageTag) : [];
	const src = sources[failedCount];
	const hue = hueFor(name);

	return (
		<span
			className={`relative inline-flex shrink-0 select-none items-center justify-center overflow-hidden rounded-full font-semibold text-white ${className}`}
			style={{
				backgroundImage: `linear-gradient(135deg, hsl(${hue} 60% 48%), hsl(${(hue + 45) % 360} 65% 34%))`,
			}}
		>
			{src ? (
				<img
					src={src}
					alt=""
					draggable={false}
					className="size-full object-cover"
					onError={() => setFailedCount((count) => count + 1)}
				/>
			) : (
				<span aria-hidden>{initialsFor(name)}</span>
			)}
		</span>
	);
}

/** Round profile picture with a coloured initials fallback. */
export function UserAvatar(props: UserAvatarProps) {
	// Keyed so the "image failed" state resets if the user or picture changes.
	return (
		<AvatarInner key={`${props.userId}:${props.imageTag ?? ""}`} {...props} />
	);
}
