/**
 * The viewer's display settings, as plain data. No browser APIs in here, so it can be tested on its own.
 * The store that saves and applies them is in settings.ts.
 */

export const ACCENTS = [
	{ id: "sky", label: "Sky", color: "#6ea8ff" },
	{ id: "violet", label: "Violet", color: "#a78bfa" },
	{ id: "rose", label: "Rose", color: "#fb7185" },
	{ id: "orange", label: "Orange", color: "#fb923c" },
	{ id: "amber", label: "Amber", color: "#fbbf24" },
	{ id: "emerald", label: "Emerald", color: "#34d399" },
	{ id: "teal", label: "Teal", color: "#2dd4bf" },
] as const;

export type AccentId = (typeof ACCENTS)[number]["id"] | "custom";

export const HOME_ROW_IDS = ["continue", "nextup", "recent"] as const;
export type HomeRowId = (typeof HOME_ROW_IDS)[number];

export const HOME_ROW_LABELS: Record<HomeRowId, string> = {
	continue: "Continue Watching",
	nextup: "Next Up",
	recent: "Recently Added",
};

export type Corners = "round" | "soft" | "square";
export type PosterSize = "small" | "medium" | "large";
export type GlassStrength = "clear" | "standard" | "frosted";
export type HeroStyle = "full" | "compact";
export type FeaturedSource = "recent" | "popular" | "mixed";
export type SeasonLayout = "list" | "row";
export type MotionSetting = "system" | "reduced";

export type HomeRowSetting = { id: HomeRowId; visible: boolean };

export type Settings = {
	accent: AccentId;
	/** Used when accent is "custom". #rrggbb. */
	customAccent: string;
	corners: Corners;
	posterSize: PosterSize;
	glass: GlassStrength;
	/** Show the title and year under each poster. */
	showTitles: boolean;
	heroStyle: HeroStyle;
	/** Order matters. */
	homeRows: HomeRowSetting[];
	featuredSource: FeaturedSource;
	/** In the popular bar, also show titles that aren't in the library. */
	featuredDiscover: boolean;
	seasonLayout: SeasonLayout;
	motion: MotionSetting;
};

export const DEFAULT_ACCENT = "#6ea8ff";

export const DEFAULT_SETTINGS: Settings = {
	accent: "sky",
	customAccent: DEFAULT_ACCENT,
	corners: "soft",
	posterSize: "medium",
	glass: "standard",
	showTitles: true,
	heroStyle: "full",
	homeRows: HOME_ROW_IDS.map((id) => ({ id, visible: true })),
	featuredSource: "popular",
	featuredDiscover: true,
	seasonLayout: "list",
	motion: "system",
};

const HEX = /^#[0-9a-f]{6}$/i;

export function isHexColor(value: unknown): value is string {
	return typeof value === "string" && HEX.test(value);
}

function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === "object" && value !== null;
}

function pick<T extends string>(
	value: unknown,
	allowed: readonly T[],
	fallback: T,
): T {
	return allowed.find((option) => option === value) ?? fallback;
}

/** Turn whatever was saved (possibly old, partial or damaged) into valid settings. */
export function sanitizeSettings(raw: unknown): Settings {
	const input = isRecord(raw) ? raw : {};
	const d = DEFAULT_SETTINGS;

	const accentIds = [...ACCENTS.map((accent) => accent.id), "custom"] as const;

	const rows: HomeRowSetting[] = [];
	const seen = new Set<HomeRowId>();
	if (Array.isArray(input.homeRows)) {
		for (const entry of input.homeRows) {
			if (!isRecord(entry)) continue;
			const id = HOME_ROW_IDS.find((rowId) => rowId === entry.id);
			if (!id || seen.has(id)) continue;
			seen.add(id);
			rows.push({ id, visible: entry.visible !== false });
		}
	}
	for (const id of HOME_ROW_IDS) {
		if (!seen.has(id)) rows.push({ id, visible: true });
	}

	return {
		accent: pick<AccentId>(input.accent, accentIds, d.accent),
		customAccent: isHexColor(input.customAccent)
			? input.customAccent
			: d.customAccent,
		corners: pick(input.corners, ["round", "soft", "square"], d.corners),
		posterSize: pick(
			input.posterSize,
			["small", "medium", "large"],
			d.posterSize,
		),
		glass: pick(input.glass, ["clear", "standard", "frosted"], d.glass),
		showTitles:
			typeof input.showTitles === "boolean" ? input.showTitles : d.showTitles,
		heroStyle: pick(input.heroStyle, ["full", "compact"], d.heroStyle),
		homeRows: rows,
		featuredSource: pick(
			input.featuredSource,
			["recent", "popular", "mixed"],
			d.featuredSource,
		),
		featuredDiscover:
			typeof input.featuredDiscover === "boolean"
				? input.featuredDiscover
				: d.featuredDiscover,
		seasonLayout: pick(input.seasonLayout, ["list", "row"], d.seasonLayout),
		motion: pick(input.motion, ["system", "reduced"], d.motion),
	};
}

/** The colour for the chosen accent. */
export function accentColor(settings: Settings): string {
	if (settings.accent === "custom") {
		return isHexColor(settings.customAccent)
			? settings.customAccent
			: DEFAULT_ACCENT;
	}
	return (
		ACCENTS.find((accent) => accent.id === settings.accent)?.color ??
		DEFAULT_ACCENT
	);
}

/** Mix a #rrggbb colour towards white. 0 leaves it alone, 1 is white. */
export function lighten(hex: string, amount: number): string {
	const channel = (start: number) => {
		const value = Number.parseInt(hex.slice(start, start + 2), 16);
		const mixed = Math.round(value + (255 - value) * amount);
		return mixed.toString(16).padStart(2, "0");
	};
	return `#${channel(1)}${channel(3)}${channel(5)}`;
}

const RADIUS: Record<Corners, string> = {
	round: "22px",
	soft: "14px",
	square: "4px",
};

const CARD_SCALE: Record<PosterSize, string> = {
	small: "0.85",
	medium: "1",
	large: "1.2",
};

const GLASS: Record<
	GlassStrength,
	{ blur: string; fill: number; strong: number }
> = {
	clear: { blur: "12px", fill: 0.04, strong: 0.08 },
	standard: { blur: "24px", fill: 0.06, strong: 0.1 },
	frosted: { blur: "40px", fill: 0.12, strong: 0.18 },
};

/** The CSS variables that carry these settings. They are set on the page's root element. */
export function cssVars(settings: Settings): Record<string, string> {
	const accent = accentColor(settings);
	const glass = GLASS[settings.glass];
	return {
		"--color-accent": accent,
		"--color-accent-strong": lighten(accent, 0.3),
		"--radius-card": RADIUS[settings.corners],
		"--card-scale": CARD_SCALE[settings.posterSize],
		"--glass-blur": glass.blur,
		"--color-glass": `rgb(255 255 255 / ${glass.fill})`,
		"--color-glass-strong": `rgb(255 255 255 / ${glass.strong})`,
	};
}

/** Move a home row one place up or down. */
export function moveRow(
	rows: HomeRowSetting[],
	id: HomeRowId,
	direction: -1 | 1,
): HomeRowSetting[] {
	const from = rows.findIndex((row) => row.id === id);
	const to = from + direction;
	if (from < 0 || to < 0 || to >= rows.length) return rows;
	const next = [...rows];
	const [moved] = next.splice(from, 1);
	if (moved) next.splice(to, 0, moved);
	return next;
}
