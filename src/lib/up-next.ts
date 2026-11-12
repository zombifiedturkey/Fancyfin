/** How long before the end the "Up next" card appears. */
export const UP_NEXT_LEAD_S = 30;
/** Shorter videos only show the card once they have ended. */
const MIN_LENGTH_S = 120;

/** Whether the "Up next" card should be on screen. */
export function upNextVisible(
	time: number,
	duration: number,
	ended: boolean,
): boolean {
	if (ended) return true;
	if (!(duration >= MIN_LENGTH_S)) return false;
	return duration - time <= UP_NEXT_LEAD_S;
}
