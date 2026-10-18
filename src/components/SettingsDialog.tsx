import type { ReactNode } from "react";
import { resetSettings, updateSettings, useSettings } from "../lib/settings";
import {
	ACCENTS,
	DEFAULT_ACCENT,
	HOME_ROW_LABELS,
	isHexColor,
	moveRow,
} from "../lib/settings-model";
import { useTmdbEnabled } from "../lib/tmdb";
import { Segmented, Switch } from "./controls";
import { Modal } from "./Modal";

function Section({ title, children }: { title: string; children: ReactNode }) {
	return (
		<section className="mt-7 first:mt-0">
			<h3 className="text-xs font-semibold uppercase tracking-[0.18em] text-ink-faint">
				{title}
			</h3>
			<div className="mt-4 space-y-5">{children}</div>
		</section>
	);
}

function Setting({
	label,
	hint,
	children,
}: {
	label: string;
	hint?: string;
	children: ReactNode;
}) {
	return (
		<div>
			<p className="text-sm font-medium">{label}</p>
			{hint && <p className="mt-0.5 text-xs text-ink-faint">{hint}</p>}
			<div className="mt-2.5">{children}</div>
		</div>
	);
}

const smallButton =
	"glass flex size-8 cursor-pointer items-center justify-center rounded-full text-sm text-ink transition-colors hover:bg-glass-strong disabled:cursor-default disabled:opacity-30";

/** Looks, home screen and season settings. Changes apply immediately and are saved in this browser. */
export function SettingsDialog({ onClose }: { onClose: () => void }) {
	const settings = useSettings();
	const tmdb = useTmdbEnabled();
	const customChosen = settings.accent === "custom";

	return (
		<Modal title="Settings" onClose={onClose} size="lg">
			<p className="-mt-1 mb-6 text-sm text-ink-muted">
				Changes apply straight away. They're saved in this browser only.
			</p>

			<Section title="Look">
				<Setting label="Accent colour">
					<div className="flex flex-wrap items-center gap-3">
						{ACCENTS.map((accent) => (
							<button
								key={accent.id}
								type="button"
								aria-label={accent.label}
								aria-pressed={settings.accent === accent.id}
								title={accent.label}
								onClick={() => updateSettings({ accent: accent.id })}
								style={{ backgroundColor: accent.color }}
								className={`size-8 cursor-pointer rounded-full transition-transform hover:scale-110 ${
									settings.accent === accent.id
										? "ring-2 ring-white ring-offset-2 ring-offset-canvas"
										: ""
								}`}
							/>
						))}
						<label
							className={`flex cursor-pointer items-center gap-2 rounded-pill px-3 py-1.5 text-sm ${
								customChosen ? "bg-ink text-canvas" : "glass text-ink-muted"
							}`}
						>
							<input
								type="color"
								aria-label="Custom accent colour"
								value={
									isHexColor(settings.customAccent)
										? settings.customAccent
										: DEFAULT_ACCENT
								}
								onChange={(event) =>
									updateSettings({
										accent: "custom",
										customAccent: event.target.value,
									})
								}
								className="size-5 cursor-pointer rounded border-0 bg-transparent p-0"
							/>
							Custom
						</label>
					</div>
				</Setting>

				<Setting label="Poster corners">
					<Segmented
						label="Poster corners"
						value={settings.corners}
						onChange={(corners) => updateSettings({ corners })}
						options={[
							{ value: "round", label: "Rounded" },
							{ value: "soft", label: "Soft" },
							{ value: "square", label: "Square" },
						]}
					/>
				</Setting>

				<Setting label="Poster size">
					<Segmented
						label="Poster size"
						value={settings.posterSize}
						onChange={(posterSize) => updateSettings({ posterSize })}
						options={[
							{ value: "small", label: "Small" },
							{ value: "medium", label: "Medium" },
							{ value: "large", label: "Large" },
						]}
					/>
				</Setting>

				<Setting
					label="Glass"
					hint="How strongly panels blur and tint what's behind them."
				>
					<Segmented
						label="Glass strength"
						value={settings.glass}
						onChange={(glass) => updateSettings({ glass })}
						options={[
							{ value: "clear", label: "Clear" },
							{ value: "standard", label: "Standard" },
							{ value: "frosted", label: "Frosted" },
						]}
					/>
				</Setting>

				<Switch
					checked={settings.showTitles}
					onChange={(showTitles) => updateSettings({ showTitles })}
				>
					Show titles under posters
				</Switch>

				<Setting label="Animations">
					<Segmented
						label="Animations"
						value={settings.motion}
						onChange={(motion) => updateSettings({ motion })}
						options={[
							{ value: "system", label: "Follow my device" },
							{ value: "reduced", label: "Keep to a minimum" },
						]}
					/>
				</Setting>
			</Section>

			<Section title="Home">
				<Setting label="Top banner size">
					<Segmented
						label="Top banner size"
						value={settings.heroStyle}
						onChange={(heroStyle) => updateSettings({ heroStyle })}
						options={[
							{ value: "full", label: "Full" },
							{ value: "compact", label: "Compact" },
						]}
					/>
				</Setting>

				<Setting
					label="Featured banner shows"
					hint={
						tmdb
							? "Popular titles come from TMDB and are matched to your library."
							: "TMDB isn't set up on this server, so the banner shows recently added titles."
					}
				>
					<Segmented
						label="Featured banner source"
						value={settings.featuredSource}
						onChange={(featuredSource) => updateSettings({ featuredSource })}
						options={[
							{ value: "popular", label: "Popular" },
							{ value: "mixed", label: "Popular + recent" },
							{ value: "recent", label: "Recently added" },
						]}
					/>
				</Setting>

				<Switch
					checked={settings.featuredDiscover}
					onChange={(featuredDiscover) => updateSettings({ featuredDiscover })}
				>
					Include popular titles that aren't in my library
				</Switch>

				<Setting
					label="Home rows"
					hint="Reorder or hide the rows under the banner."
				>
					<ul className="space-y-2">
						{settings.homeRows.map((row, index) => (
							<li
								key={row.id}
								className="glass flex items-center gap-3 rounded-card px-4 py-2.5"
							>
								<span className="min-w-0 flex-1 text-sm">
									{HOME_ROW_LABELS[row.id]}
								</span>
								<button
									type="button"
									aria-label={`Move ${HOME_ROW_LABELS[row.id]} up`}
									disabled={index === 0}
									onClick={() =>
										updateSettings({
											homeRows: moveRow(settings.homeRows, row.id, -1),
										})
									}
									className={smallButton}
								>
									↑
								</button>
								<button
									type="button"
									aria-label={`Move ${HOME_ROW_LABELS[row.id]} down`}
									disabled={index === settings.homeRows.length - 1}
									onClick={() =>
										updateSettings({
											homeRows: moveRow(settings.homeRows, row.id, 1),
										})
									}
									className={smallButton}
								>
									↓
								</button>
								<Switch
									checked={row.visible}
									onChange={(visible) =>
										updateSettings({
											homeRows: settings.homeRows.map((entry) =>
												entry.id === row.id ? { ...entry, visible } : entry,
											),
										})
									}
								>
									<span className="sr-only">
										Show {HOME_ROW_LABELS[row.id]}
									</span>
								</Switch>
							</li>
						))}
					</ul>
				</Setting>
			</Section>

			<Section title="TV seasons">
				<Setting label="Episode layout">
					<Segmented
						label="Episode layout"
						value={settings.seasonLayout}
						onChange={(seasonLayout) => updateSettings({ seasonLayout })}
						options={[
							{ value: "list", label: "List with descriptions" },
							{ value: "row", label: "Row of cards" },
						]}
					/>
				</Setting>
			</Section>

			<div className="mt-8 flex">
				<button
					type="button"
					onClick={resetSettings}
					className="glass cursor-pointer rounded-pill px-5 py-2 text-sm font-semibold text-ink transition-colors hover:bg-glass-strong"
				>
					Reset to defaults
				</button>
			</div>
		</Modal>
	);
}
