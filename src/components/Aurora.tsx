/** Soft coloured glow used behind the hero and login screen. Stands in for item artwork. */
export function Aurora({ className = "" }: { className?: string }) {
	return (
		<div
			aria-hidden
			className={`absolute inset-0 -z-10 ${className}`}
			style={{
				background: [
					"radial-gradient(60% 80% at 75% 20%, rgb(110 168 255 / 0.35), transparent 60%)",
					"radial-gradient(50% 70% at 20% 30%, rgb(150 110 255 / 0.28), transparent 60%)",
					"radial-gradient(70% 60% at 50% 100%, rgb(60 220 190 / 0.14), transparent 60%)",
					"var(--color-canvas)",
				].join(", "),
			}}
		/>
	);
}
