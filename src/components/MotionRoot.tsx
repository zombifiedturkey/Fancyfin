import { MotionConfig } from "motion/react";
import type { ReactNode } from "react";
import { useSettings } from "../lib/settings";

/** Applies the "reduce motion" setting to every animation in the app. */
export function MotionRoot({ children }: { children: ReactNode }) {
	const { motion } = useSettings();
	return (
		<MotionConfig reducedMotion={motion === "reduced" ? "always" : "user"}>
			{children}
		</MotionConfig>
	);
}
