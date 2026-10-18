/** Full-screen placeholder shown for the brief moment the saved session is being verified. */
export function Splash() {
	return (
		<div className="grid min-h-dvh place-items-center bg-canvas">
			<span
				role="status"
				aria-label="Loading"
				className="size-8 animate-pulse rounded-full bg-linear-to-br from-accent-strong to-accent"
			/>
		</div>
	);
}
