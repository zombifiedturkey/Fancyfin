import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import {
	ARR_LABEL,
	AVAILABILITY,
	type Choices,
	folderLabel,
	MONITOR,
	metaLine,
	resolveChoices,
	SERIES_TYPES,
} from "../lib/arr";
import { saveArrPrefs, useArrPrefs } from "../lib/arr-prefs";
import {
	arrForKind,
	candidatesQuery,
	describeArrError,
	optionsQuery,
	useAddToArr,
} from "../lib/arr-queries";
import { type TmdbTitle, tmdbImage } from "../lib/tmdb";
import { CheckField, SelectField } from "./controls";
import { FadeImage } from "./FadeImage";
import { Modal } from "./Modal";

type AddToArrDialogProps = {
	title: TmdbTitle;
	/** Why the dialog is showing when the person expected a quick add, such as an error. */
	notice?: string;
	onClose: () => void;
};

/** Choose where a title goes in Radarr or Sonarr, and whether to start looking for it right away. */
export function AddToArrDialog({
	title,
	notice,
	onClose,
}: AddToArrDialogProps) {
	const app = arrForKind(title.kind);
	const label = ARR_LABEL[app];
	const isMovie = app === "radarr";
	const prefs = useArrPrefs()[app];

	const options = useQuery(optionsQuery(app));
	const candidates = useQuery(candidatesQuery(app, title));
	const add = useAddToArr(title);

	const [pickedKey, setPickedKey] = useState<string>();
	const [edits, setEdits] = useState<Partial<Choices>>({});
	const [skip, setSkip] = useState(prefs.skip);

	const matches = candidates.data ?? [];
	const match = matches.find((m) => m.key === pickedKey) ?? matches[0];

	const defaults = options.data
		? resolveChoices(options.data, prefs, match)
		: undefined;
	const choices: Choices | undefined = defaults && { ...defaults, ...edits };
	const edit = <K extends keyof Choices>(
		key: K,
		value: Choices[K] | undefined,
	) => {
		if (value === undefined) return;
		setEdits((previous): Partial<Choices> => ({ ...previous, [key]: value }));
	};

	const loading = options.isPending || candidates.isPending;
	const failure = options.error ?? candidates.error ?? add.error;
	const missing =
		!loading && !failure && (!match ? "notfound" : !choices ? "nowhere" : null);

	const poster =
		match?.poster ??
		(title.poster ? tmdbImage(title.poster, "w342") : undefined);
	const name = match?.title ?? title.title;
	const meta = match
		? metaLine(match)
		: [title.year, title.kind === "tv" ? "TV series" : "Movie"]
				.filter(Boolean)
				.join(" · ");
	const overview = match?.overview ?? title.overview;
	const already = match?.existingId !== undefined;

	const submit = () => {
		if (!match || !choices || already) return;
		add.mutate(
			{ candidate: match, choices },
			{
				onSuccess: () => {
					saveArrPrefs(app, { ...choices, skip });
					onClose();
				},
			},
		);
	};

	return (
		<Modal title={`Add to ${label}`} onClose={onClose} bare>
			<div className="flex gap-5 pr-10">
				<div className="aspect-[2/3] w-24 shrink-0 overflow-hidden rounded-card border border-glass-border bg-glass shadow-card sm:w-28">
					{poster && (
						<FadeImage
							key={poster}
							src={poster}
							alt=""
							className="size-full object-cover"
						/>
					)}
				</div>
				<div className="min-w-0">
					<p className="text-xs font-semibold uppercase tracking-[0.2em] text-accent-strong">
						Add to {label}
					</p>
					<h2 className="mt-1.5 text-3xl font-extrabold leading-none tracking-tight sm:text-4xl">
						{name}
					</h2>
					{meta && <p className="mt-2.5 text-sm text-ink-muted">{meta}</p>}
					{overview && (
						<p className="mt-2.5 line-clamp-3 text-sm text-ink/80">
							{overview}
						</p>
					)}
				</div>
			</div>

			<div className="mt-6 space-y-4">
				{loading ? (
					<div className="space-y-4" aria-hidden>
						<div className="shimmer h-[68px] rounded-card" />
						<div className="shimmer h-[68px] rounded-card" />
						<div className="grid grid-cols-2 gap-4">
							<div className="shimmer h-[68px] rounded-card" />
							<div className="shimmer h-[68px] rounded-card" />
						</div>
					</div>
				) : choices && match ? (
					<>
						<SelectField
							label="Match — not the right one? Pick another"
							value={match.key}
							onChange={setPickedKey}
							disabled={matches.length < 2}
						>
							{matches.map((m) => (
								<option key={m.key} value={m.key}>
									{m.title}
									{m.year ? ` (${m.year})` : ""}
									{m.existingId ? " — already added" : ""}
								</option>
							))}
						</SelectField>

						<SelectField
							label="Root folder"
							value={choices.rootFolder}
							onChange={(rootFolder) => edit("rootFolder", rootFolder)}
						>
							{options.data?.folders.map((folder) => (
								<option key={folder.path} value={folder.path}>
									{folderLabel(folder)}
								</option>
							))}
						</SelectField>

						<div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
							<SelectField
								label="Quality profile"
								value={String(choices.qualityProfileId)}
								onChange={(value) => edit("qualityProfileId", Number(value))}
							>
								{options.data?.profiles.map((profile) => (
									<option key={profile.id} value={profile.id}>
										{profile.name}
									</option>
								))}
							</SelectField>

							{isMovie ? (
								<SelectField
									label="Minimum availability"
									value={choices.minimumAvailability}
									onChange={(value) =>
										edit(
											"minimumAvailability",
											AVAILABILITY.find((a) => a.value === value)?.value,
										)
									}
								>
									{AVAILABILITY.map((a) => (
										<option key={a.value} value={a.value}>
											{a.label}
										</option>
									))}
								</SelectField>
							) : (
								<>
									<SelectField
										label="Monitor"
										value={choices.monitor}
										onChange={(value) =>
											edit(
												"monitor",
												MONITOR.find((m) => m.value === value)?.value,
											)
										}
									>
										{MONITOR.map((m) => (
											<option key={m.value} value={m.value}>
												{m.label}
											</option>
										))}
									</SelectField>
									<SelectField
										label="Series type"
										value={choices.seriesType}
										onChange={(value) =>
											edit(
												"seriesType",
												SERIES_TYPES.find((t) => t.value === value)?.value,
											)
										}
									>
										{SERIES_TYPES.map((t) => (
											<option key={t.value} value={t.value}>
												{t.label}
											</option>
										))}
									</SelectField>
								</>
							)}
						</div>

						<div className="space-y-3 pt-1">
							<CheckField
								checked={choices.search}
								onChange={(search) => edit("search", search)}
							>
								{isMovie
									? "Start searching for this movie straight away"
									: "Start searching for missing episodes straight away"}
							</CheckField>
							<CheckField checked={skip} onChange={setSkip}>
								Next time, skip this window and add with these settings
							</CheckField>
						</div>
					</>
				) : null}

				{already && (
					<p className="text-sm text-ink-muted">
						{label} already has this one, so there's nothing to add.
					</p>
				)}
				{missing === "notfound" && (
					<p role="alert" className="text-sm text-danger">
						{label} couldn't find this title. Its metadata source may not have
						it yet.
					</p>
				)}
				{missing === "nowhere" && (
					<p role="alert" className="text-sm text-danger">
						{label} has no root folder or quality profile set up, so there's
						nowhere to add this. Set one up in {label} first.
					</p>
				)}
				{failure ? (
					<p role="alert" className="text-sm text-danger">
						{describeArrError(failure)}
					</p>
				) : notice && !add.isPending ? (
					<p role="alert" className="text-sm text-danger">
						{notice}
					</p>
				) : null}
			</div>

			<div className="mt-7 flex flex-wrap items-center gap-3">
				<button
					type="button"
					onClick={onClose}
					className="glass cursor-pointer rounded-pill px-6 py-3 text-sm font-semibold text-ink transition-colors hover:bg-glass-strong"
				>
					Cancel
				</button>
				<button
					type="button"
					onClick={submit}
					disabled={!match || !choices || already || add.isPending}
					className="cursor-pointer rounded-pill bg-ink px-6 py-3 text-sm font-semibold text-canvas transition-opacity hover:opacity-90 disabled:cursor-default disabled:opacity-40"
				>
					{add.isPending ? "Adding…" : `Add to ${label}`}
				</button>
			</div>
		</Modal>
	);
}
