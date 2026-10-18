import { describe, expect, it } from "vitest";
import {
	accentColor,
	cssVars,
	DEFAULT_SETTINGS,
	lighten,
	moveRow,
	sanitizeSettings,
} from "./settings-model";

describe("sanitizeSettings", () => {
	it("gives the defaults for nothing or nonsense", () => {
		expect(sanitizeSettings(undefined)).toEqual(DEFAULT_SETTINGS);
		expect(sanitizeSettings("hello")).toEqual(DEFAULT_SETTINGS);
		expect(sanitizeSettings({ corners: "pointy", accent: 7 })).toEqual(
			DEFAULT_SETTINGS,
		);
	});

	it("keeps valid choices and fills in the rest", () => {
		const result = sanitizeSettings({ corners: "square", showTitles: false });
		expect(result.corners).toBe("square");
		expect(result.showTitles).toBe(false);
		expect(result.posterSize).toBe("medium");
	});

	it("repairs the home rows: unknown dropped, repeats dropped, missing added", () => {
		const result = sanitizeSettings({
			homeRows: [
				{ id: "recent", visible: false },
				{ id: "bogus", visible: true },
				{ id: "recent", visible: true },
				{ id: "continue" },
			],
		});
		expect(result.homeRows).toEqual([
			{ id: "recent", visible: false },
			{ id: "continue", visible: true },
			{ id: "nextup", visible: true },
		]);
	});

	it("only accepts #rrggbb for a custom colour", () => {
		expect(sanitizeSettings({ customAccent: "red" }).customAccent).toBe(
			DEFAULT_SETTINGS.customAccent,
		);
		expect(sanitizeSettings({ customAccent: "#12ab9F" }).customAccent).toBe(
			"#12ab9F",
		);
	});
});

describe("accent and css variables", () => {
	it("picks the preset or the custom colour", () => {
		expect(accentColor({ ...DEFAULT_SETTINGS, accent: "rose" })).toBe(
			"#fb7185",
		);
		expect(
			accentColor({
				...DEFAULT_SETTINGS,
				accent: "custom",
				customAccent: "#112233",
			}),
		).toBe("#112233");
	});

	it("lightens towards white", () => {
		expect(lighten("#000000", 0.5)).toBe("#808080");
		expect(lighten("#ffffff", 0.3)).toBe("#ffffff");
	});

	it("turns settings into variables", () => {
		const vars = cssVars({
			...DEFAULT_SETTINGS,
			corners: "round",
			posterSize: "large",
			glass: "frosted",
		});
		expect(vars["--radius-card"]).toBe("22px");
		expect(vars["--card-scale"]).toBe("1.2");
		expect(vars["--glass-blur"]).toBe("40px");
		expect(vars["--color-glass"]).toBe("rgb(255 255 255 / 0.12)");
	});
});

describe("moveRow", () => {
	const rows = DEFAULT_SETTINGS.homeRows;
	it("moves a row and stops at the ends", () => {
		expect(moveRow(rows, "nextup", -1).map((r) => r.id)).toEqual([
			"nextup",
			"continue",
			"recent",
		]);
		expect(moveRow(rows, "continue", -1)).toBe(rows);
		expect(moveRow(rows, "recent", 1)).toBe(rows);
	});
});
