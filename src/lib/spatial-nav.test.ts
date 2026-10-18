import { describe, expect, it } from "vitest";
import { type Box, pickNext } from "./spatial-nav";

/** A 3 by 3 grid of 100px boxes with 20px gaps, row by row. */
const grid: Box[] = Array.from({ length: 9 }, (_, i) => {
	const left = (i % 3) * 120;
	const top = Math.floor(i / 3) * 120;
	return { left, top, right: left + 100, bottom: top + 100 };
});

function from(index: number, direction: Parameters<typeof pickNext>[2]) {
	const others = grid.filter((_, i) => i !== index);
	const picked = pickNext(grid[index] as Box, others, direction);
	if (picked < 0) return -1;
	return grid.indexOf(others[picked] as Box);
}

describe("pickNext", () => {
	it("moves to the neighbour in each direction", () => {
		expect(from(4, "right")).toBe(5);
		expect(from(4, "left")).toBe(3);
		expect(from(4, "down")).toBe(7);
		expect(from(4, "up")).toBe(1);
	});

	it("says there is nowhere to go at an edge", () => {
		expect(from(0, "left")).toBe(-1);
		expect(from(0, "up")).toBe(-1);
		expect(from(8, "right")).toBe(-1);
		expect(from(8, "down")).toBe(-1);
	});

	it("prefers a box in line over a nearer one off to the side", () => {
		const start: Box = { left: 0, top: 0, right: 100, bottom: 100 };
		const inLine: Box = { left: 300, top: 0, right: 400, bottom: 100 };
		const nearButLower: Box = { left: 110, top: 400, right: 210, bottom: 500 };
		expect(pickNext(start, [nearButLower, inLine], "right")).toBe(1);
	});

	it("moves down into the nearest box under a wide one", () => {
		const wide: Box = { left: 0, top: 0, right: 360, bottom: 100 };
		const below = grid
			.slice(0, 3)
			.map((b) => ({ ...b, top: b.top + 200, bottom: b.bottom + 200 }));
		const picked = pickNext(wide, below, "down");
		expect(picked >= 0).toBe(true);
	});

	it("ignores boxes behind", () => {
		expect(pickNext(grid[4] as Box, [grid[3] as Box], "right")).toBe(-1);
	});
});
