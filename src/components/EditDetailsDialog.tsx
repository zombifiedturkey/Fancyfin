import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import { type FormEvent, type ReactNode, useId, useState } from "react";
import { applyForm, type EditForm, toForm } from "../lib/edit-item";
import {
	describeError,
	useRatingNames,
	useUpdateItem,
} from "../lib/edit-queries";
import { Modal } from "./Modal";

const input =
	"glass w-full rounded-[14px] px-4 py-2.5 text-sm text-ink outline-none placeholder:text-ink-faint focus:border-accent";
const button =
	"cursor-pointer rounded-pill px-5 py-2 text-sm font-semibold transition-colors disabled:cursor-wait disabled:opacity-60";

function Field({
	label,
	hint,
	className = "",
	children,
}: {
	label: string;
	hint?: string;
	className?: string;
	children: (id: string) => ReactNode;
}) {
	const id = useId();
	return (
		<div className={className}>
			<label
				htmlFor={id}
				className="mb-1.5 block text-xs font-medium text-ink-muted"
			>
				{label}
			</label>
			{children(id)}
			{hint && <p className="mt-1 text-xs text-ink-faint">{hint}</p>}
		</div>
	);
}

/** Edit a title's name, dates, overview, genres and similar. Saved straight to the Jellyfin server. */
export function EditDetailsDialog({
	item,
	onClose,
}: {
	item: BaseItemDto;
	onClose: () => void;
}) {
	const [form, setForm] = useState<EditForm>(() => ({
		...toForm(item),
		// Locking keeps a later metadata refresh from undoing what was typed here.
		lock: true,
	}));
	const save = useUpdateItem(item.Id ?? "");
	const ratings = useRatingNames();
	const listId = useId();

	const set = <K extends keyof EditForm>(key: K, value: EditForm[K]) =>
		setForm((current) => ({ ...current, [key]: value }));

	const onSubmit = (event: FormEvent) => {
		event.preventDefault();
		if (!form.name.trim()) return;
		save.mutate(applyForm(item, form), { onSuccess: onClose });
	};

	return (
		<Modal title={`Edit details · ${item.Name ?? "title"}`} onClose={onClose}>
			<form onSubmit={onSubmit} className="grid gap-4 sm:grid-cols-2">
				<Field label="Title" className="sm:col-span-2">
					{(id) => (
						<input
							id={id}
							required
							value={form.name}
							onChange={(event) => set("name", event.target.value)}
							className={input}
						/>
					)}
				</Field>
				<Field label="Original title">
					{(id) => (
						<input
							id={id}
							value={form.originalTitle}
							onChange={(event) => set("originalTitle", event.target.value)}
							className={input}
						/>
					)}
				</Field>
				<Field label="Sort title" hint="Used when ordering the library.">
					{(id) => (
						<input
							id={id}
							value={form.sortName}
							onChange={(event) => set("sortName", event.target.value)}
							className={input}
						/>
					)}
				</Field>
				<Field label="Release date">
					{(id) => (
						<input
							id={id}
							type="date"
							value={form.premiereDate}
							onChange={(event) => set("premiereDate", event.target.value)}
							className={`${input} [color-scheme:dark]`}
						/>
					)}
				</Field>
				<div className="grid grid-cols-2 gap-4">
					<Field label="Age rating">
						{(id) => (
							<>
								<input
									id={id}
									list={listId}
									value={form.officialRating}
									onChange={(event) =>
										set("officialRating", event.target.value)
									}
									className={input}
								/>
								<datalist id={listId}>
									{(ratings.data ?? []).map((name) => (
										<option key={name} value={name} />
									))}
								</datalist>
							</>
						)}
					</Field>
					<Field label="Rating (0–10)">
						{(id) => (
							<input
								id={id}
								type="number"
								min={0}
								max={10}
								step={0.1}
								value={form.communityRating}
								onChange={(event) => set("communityRating", event.target.value)}
								className={input}
							/>
						)}
					</Field>
				</div>
				<Field label="Tagline" className="sm:col-span-2">
					{(id) => (
						<input
							id={id}
							value={form.tagline}
							onChange={(event) => set("tagline", event.target.value)}
							className={input}
						/>
					)}
				</Field>
				<Field label="Overview" className="sm:col-span-2">
					{(id) => (
						<textarea
							id={id}
							rows={5}
							value={form.overview}
							onChange={(event) => set("overview", event.target.value)}
							className={`${input} resize-y`}
						/>
					)}
				</Field>
				<Field label="Genres" hint="Separate with commas.">
					{(id) => (
						<input
							id={id}
							value={form.genres}
							onChange={(event) => set("genres", event.target.value)}
							className={input}
						/>
					)}
				</Field>
				<Field label="Tags" hint="Separate with commas.">
					{(id) => (
						<input
							id={id}
							value={form.tags}
							onChange={(event) => set("tags", event.target.value)}
							className={input}
						/>
					)}
				</Field>

				<label className="flex cursor-pointer items-start gap-3 text-sm sm:col-span-2">
					<input
						type="checkbox"
						checked={form.lock}
						onChange={(event) => set("lock", event.target.checked)}
						className="mt-0.5 size-4 accent-[var(--color-accent)]"
					/>
					<span>
						Lock these details
						<span className="block text-xs text-ink-faint">
							Stops a metadata refresh from overwriting your changes.
						</span>
					</span>
				</label>

				{save.isError && (
					<p role="alert" className="text-sm text-danger sm:col-span-2">
						{describeError(save.error)}
					</p>
				)}

				<div className="flex justify-end gap-3 sm:col-span-2">
					<button
						type="button"
						disabled={save.isPending}
						onClick={onClose}
						className={`${button} glass text-ink`}
					>
						Cancel
					</button>
					<button
						type="submit"
						disabled={save.isPending || !form.name.trim()}
						className={`${button} bg-ink text-canvas`}
					>
						{save.isPending ? "Saving…" : "Save"}
					</button>
				</div>
			</form>
		</Modal>
	);
}
