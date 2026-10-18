import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import type { RemoteSearchResult } from "@jellyfin/sdk/lib/generated-client/models/remote-search-result";
import { type FormEvent, useState } from "react";
import { useApplyMatch, useRemoteSearch } from "../lib/admin-queries";
import { FadeImage } from "./FadeImage";
import { Modal } from "./Modal";

const field =
	"glass rounded-pill px-4 py-2 text-sm text-ink outline-none placeholder:text-ink-faint focus:border-accent";
const button =
	"cursor-pointer rounded-pill px-5 py-2 text-sm font-semibold transition-colors disabled:cursor-wait disabled:opacity-60";

function providerLabel(result: RemoteSearchResult): string {
	const ids = Object.entries(result.ProviderIds ?? {})
		.filter(([, value]) => value)
		.slice(0, 2)
		.map(([key, value]) => `${key} ${value}`);
	return [result.SearchProviderName, ...ids].filter(Boolean).join(" · ");
}

function ResultRow({
	result,
	onChoose,
}: {
	result: RemoteSearchResult;
	onChoose: () => void;
}) {
	return (
		<li className="flex gap-4 rounded-card border border-glass-border bg-glass p-3">
			<div className="relative aspect-[2/3] w-16 shrink-0 overflow-hidden rounded-lg bg-glass-strong">
				{result.ImageUrl && (
					<FadeImage
						key={result.ImageUrl}
						src={result.ImageUrl}
						className="absolute inset-0 size-full object-cover"
					/>
				)}
			</div>
			<div className="min-w-0 flex-1">
				<p className="font-medium">
					{result.Name}
					{result.ProductionYear ? (
						<span className="text-ink-muted"> ({result.ProductionYear})</span>
					) : null}
				</p>
				<p className="mt-0.5 text-xs text-ink-faint">{providerLabel(result)}</p>
				{result.Overview && (
					<p className="mt-2 line-clamp-2 text-sm text-ink-muted">
						{result.Overview}
					</p>
				)}
			</div>
			<button
				type="button"
				onClick={onChoose}
				className={`${button} h-9 self-center bg-ink text-canvas`}
			>
				Choose
			</button>
		</li>
	);
}

/** Search the metadata providers and re-match a title to the right entry. */
export function IdentifyDialog({
	item,
	onClose,
}: {
	item: BaseItemDto;
	onClose: () => void;
}) {
	const itemId = item.Id ?? "";
	const [name, setName] = useState(item.Name ?? "");
	const [year, setYear] = useState(item.ProductionYear?.toString() ?? "");
	// What the search is currently running with. Changes only when the form is submitted.
	const [submitted, setSubmitted] = useState({ name, year });
	const [chosen, setChosen] = useState<RemoteSearchResult | undefined>();
	const [replaceImages, setReplaceImages] = useState(true);

	const parsedYear = Number.parseInt(submitted.year, 10);
	const search = useRemoteSearch(
		item,
		submitted.name,
		Number.isNaN(parsedYear) ? undefined : parsedYear,
	);
	const apply = useApplyMatch(itemId);

	const onSubmit = (event: FormEvent) => {
		event.preventDefault();
		setSubmitted({ name: name.trim(), year: year.trim() });
	};

	return (
		<Modal title={`Fix match · ${item.Name ?? "title"}`} onClose={onClose}>
			{chosen ? (
				<div>
					<p className="text-sm text-ink-muted">
						This replaces the details for this title with the entry below, and
						re-downloads its metadata. It changes your library on the server.
					</p>
					<ul className="mt-4">
						<ResultRow result={chosen} onChoose={() => undefined} />
					</ul>
					<label className="mt-4 flex cursor-pointer items-center gap-3 text-sm">
						<input
							type="checkbox"
							checked={replaceImages}
							onChange={(event) => setReplaceImages(event.target.checked)}
							className="size-4 accent-[var(--color-accent)]"
						/>
						Also replace the posters and artwork
					</label>
					{apply.isError && (
						<p className="mt-4 text-sm text-danger">
							The server couldn't apply that match. Check you have permission
							and try again.
						</p>
					)}
					<div className="mt-6 flex justify-end gap-3">
						<button
							type="button"
							disabled={apply.isPending}
							onClick={() => setChosen(undefined)}
							className={`${button} glass text-ink`}
						>
							Back
						</button>
						<button
							type="button"
							disabled={apply.isPending}
							onClick={() =>
								apply.mutate(
									{ result: chosen, replaceAllImages: replaceImages },
									{ onSuccess: onClose },
								)
							}
							className={`${button} bg-ink text-canvas`}
						>
							{apply.isPending ? "Applying…" : "Apply match"}
						</button>
					</div>
				</div>
			) : (
				<div>
					<form onSubmit={onSubmit} className="flex flex-wrap gap-2">
						<input
							aria-label="Title"
							value={name}
							onChange={(event) => setName(event.target.value)}
							placeholder="Title"
							className={`${field} min-w-0 flex-1`}
						/>
						<input
							aria-label="Year"
							value={year}
							onChange={(event) => setYear(event.target.value)}
							placeholder="Year"
							inputMode="numeric"
							className={`${field} w-24`}
						/>
						<button type="submit" className={`${button} bg-ink text-canvas`}>
							Search
						</button>
					</form>

					<div className="mt-5">
						{search.isFetching ? (
							<ul className="space-y-3" aria-hidden>
								{Array.from({ length: 3 }, (_, i) => (
									<li key={i} className="shimmer h-24 rounded-card" />
								))}
							</ul>
						) : search.isError ? (
							<p className="text-sm text-ink-muted">
								The search failed. Check that a metadata provider is set up in
								your Jellyfin library settings.
							</p>
						) : (search.data ?? []).length === 0 ? (
							<p className="text-sm text-ink-muted">
								No matches. Try a different spelling or year.
							</p>
						) : (
							<ul className="space-y-3">
								{(search.data ?? []).map((result, i) => (
									<ResultRow
										key={`${result.SearchProviderName}:${result.Name}:${i}`}
										result={result}
										onChoose={() => setChosen(result)}
									/>
								))}
							</ul>
						)}
					</div>
				</div>
			)}
		</Modal>
	);
}
