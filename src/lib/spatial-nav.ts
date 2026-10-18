/** Moving focus with the arrow keys of a TV remote, to whatever is nearest in that direction. */
export type Direction = "up" | "down" | "left" | "right";

export type Box = { left: number; top: number; right: number; bottom: number };

const center = {
	x: (box: Box) => (box.left + box.right) / 2,
	y: (box: Box) => (box.top + box.bottom) / 2,
};

/**
 * How good a move from one box to another is, lower being better, or undefined when the other box
 * isn't in that direction at all. Near ones win, and ones that line up with the current box win
 * over ones that are off to the side.
 */
function score(from: Box, to: Box, direction: Direction): number | undefined {
	const horizontal = direction === "left" || direction === "right";
	// Along the direction of travel: the gap between the facing edges, and which side it is on.
	const forward = horizontal
		? direction === "right"
			? center.x(to) - center.x(from)
			: center.x(from) - center.x(to)
		: direction === "down"
			? center.y(to) - center.y(from)
			: center.y(from) - center.y(to);
	if (forward <= 0) return undefined;

	const ahead = horizontal
		? direction === "right"
			? to.left - from.right
			: from.left - to.right
		: direction === "down"
			? to.top - from.bottom
			: from.top - to.bottom;

	// Sideways: zero when the boxes overlap on that axis, else the gap between them.
	const sideGap = horizontal
		? Math.max(0, to.top - from.bottom, from.top - to.bottom)
		: Math.max(0, to.left - from.right, from.left - to.right);
	const sideOffset = horizontal
		? Math.abs(center.y(to) - center.y(from))
		: Math.abs(center.x(to) - center.x(from));

	return Math.max(0, ahead) + sideGap * 4 + sideOffset * 0.5;
}

/** The index of the best box to move to, or -1 when there is nowhere to go that way. */
export function pickNext(
	from: Box,
	others: Box[],
	direction: Direction,
): number {
	let best = -1;
	let bestScore = Number.POSITIVE_INFINITY;
	others.forEach((box, index) => {
		const value = score(from, box, direction);
		if (value !== undefined && value < bestScore) {
			best = index;
			bestScore = value;
		}
	});
	return best;
}
