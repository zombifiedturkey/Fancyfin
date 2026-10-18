import { motion } from "motion/react";
import { type ReactNode, useEffect } from "react";
import { createPortal } from "react-dom";
import { useMotionReduced } from "../lib/settings";

/** A centred glass dialog above everything, including the title panel. Escape closes only this. */
export function Modal({
	title,
	onClose,
	children,
	size = "md",
}: {
	title: string;
	onClose: () => void;
	children: ReactNode;
	size?: "md" | "lg";
}) {
	const reduceMotion = useMotionReduced();

	// Capture phase, so Escape closes this dialog and not the panel underneath.
	useEffect(() => {
		const onKeyDown = (event: KeyboardEvent) => {
			if (event.key === "Escape") {
				event.stopPropagation();
				onClose();
			}
		};
		window.addEventListener("keydown", onKeyDown, true);
		return () => window.removeEventListener("keydown", onKeyDown, true);
	}, [onClose]);

	// Rendered on <body>: inside the title panel, its blur and animation would trap a fixed element.
	return createPortal(
		<div className="fixed inset-0 z-[70] flex items-center justify-center p-4">
			<motion.div
				aria-hidden
				className="absolute inset-0 bg-black/45 backdrop-blur-sm"
				initial={{ opacity: 0 }}
				animate={{ opacity: 1 }}
				transition={{ duration: reduceMotion ? 0 : 0.2 }}
				onClick={onClose}
			/>
			<motion.div
				role="dialog"
				aria-modal="true"
				aria-label={title}
				className={`relative flex max-h-[85dvh] w-full ${size === "lg" ? "max-w-4xl" : "max-w-2xl"} flex-col overflow-hidden rounded-[24px] border border-white/15 bg-[rgb(16_18_28/0.7)] shadow-[0_40px_120px_-20px_rgb(0_0_0/0.8),inset_0_1px_0_rgb(255_255_255/0.2)] backdrop-blur-[44px] backdrop-saturate-[1.8]`}
				initial={{ opacity: 0, scale: 0.97, y: 10 }}
				animate={{ opacity: 1, scale: 1, y: 0 }}
				transition={{
					duration: reduceMotion ? 0 : 0.22,
					ease: [0.16, 1, 0.3, 1],
				}}
			>
				<div className="flex items-center justify-between gap-4 px-6 pb-3 pt-5">
					<h2 className="text-lg font-semibold tracking-tight">{title}</h2>
					<button
						type="button"
						aria-label="Close"
						onClick={onClose}
						className="glass flex size-9 cursor-pointer items-center justify-center rounded-full text-ink transition-colors hover:bg-glass-strong"
					>
						<svg
							aria-hidden
							viewBox="0 0 24 24"
							className="size-4"
							fill="none"
							stroke="currentColor"
							strokeWidth="2.5"
							strokeLinecap="round"
						>
							<path d="M6 6l12 12M18 6L6 18" />
						</svg>
					</button>
				</div>
				<div className="overflow-y-auto px-6 pb-6">{children}</div>
			</motion.div>
		</div>,
		document.body,
	);
}
