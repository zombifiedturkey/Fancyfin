import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import { useMutationState } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { useMemo, useState } from "react";
import { EditDetailsDialog } from "../components/EditDetailsDialog";
import { FadeImage } from "../components/FadeImage";
import { IdentifyDialog } from "../components/IdentifyDialog";
import { useIsAdmin, useRefreshItem } from "../lib/admin-queries";
import { type AttentionReason, REASON_LABEL } from "../lib/attention";
import {
	type AttentionItem,
	useAttentionItems,
} from "../lib/attention-queries";
import { posterImage } from "../lib/images";

type KindFilter = "all" | "Movie" | "Series";
type ReasonFilter = "all" | AttentionReason;

const pill =
	"cursor-pointer rounded-pill px-4 py-2 text-sm font-medium transition-colors glass text-ink-muted hover:text-ink";
const pillOn = "bg-ink text-canvas hover:text-canvas";
const action =
	"cursor-pointer rounded-pill px-4 py-1.5 text-sm font-semibold transition-colors disabled:cursor-wait disabled:opacity-60";

function Row({ entry, canFix }: { entry: AttentionItem; canFix: boolean }) {
	const { item } = entry;
	const id = item.Id ?? "";
	const refresh = useRefreshItem(id);
	const [identifying, setIdentifying] = useState(false);
	const [editing, setEditing] = useState(false);
	const poster = posterImage(item, 160);
	// Matches chosen for this title run in the background; show where they are up to.
	const matchStates = useMutationState({
		filters: { mutationKey: ["apply-match", id] },
		select: (mutation) => mutation.state.status,
	});
	const applying = matchStates.includes("pending");
	const applyFailed = !applying && matchStates.at(-1) === "error";

	return (
		<li className="flex gap-4 rounded-card border border-glass-border bg-glass p-3">
			<Link
				to="/item/$id"
				params={{ id }}
				className="relative block aspect-[2/3] w-16 shrink-0 overflow-hidden rounded-lg bg-glass-strong"
			>
				{poster ? (
					<FadeImage
						src={poster}
						className="absolute inset-0 size-full object-cover"
					/>
				) : (
					<span className="absolute inset-0 grid place-items-center text-[10px] text-ink-faint">
						No poster
					</span>
				)}
			</Link>
			<div className="min-w-0 flex-1">
				<p className="font-medium">
					<Link to="/item/$id" params={{ id }} className="hover:underline">
						{item.Name}
					</Link>
					{item.ProductionYear ? (
						<span className="text-ink-muted"> ({item.ProductionYear})</span>
					) : null}
					<span className="ml-2 text-xs uppercase tracking-wide text-ink-faint">
						{item.Type === "Series" ? "Show" : "Movie"}
					</span>
				</p>
				{item.Path && (
					<p
						className="mt-0.5 truncate text-xs text-ink-faint"
						title={item.Path}
					>
						{item.Path}
					</p>
				)}
				<div className="mt-2 flex flex-wrap gap-1.5">
					{entry.reasons.map((reason) => (
						<span
							key={reason}
							className="rounded-pill bg-amber-400/15 px-2.5 py-0.5 text-xs font-medium text-amber-200"
						>
							{REASON_LABEL[reason]}
						</span>
					))}
				</div>
				{applying && (
					<p className="mt-2 text-xs text-accent">
						Applying the new match… it leaves this list when the server is done.
					</p>
				)}
				{applyFailed && (
					<p className="mt-2 text-xs text-danger">
						The server couldn't apply that match. Try Fix match again.
					</p>
				)}
				{refresh.isSuccess && (
					<p className="mt-2 text-xs text-ink-muted">
						Refresh started. This row updates when the server is done.
					</p>
				)}
				{refresh.isError && (
					<p className="mt-2 text-xs text-ink-muted">
						Couldn't start the refresh.
					</p>
				)}
			</div>
			{canFix && (
				<div className="flex shrink-0 flex-col justify-center gap-2">
					<button
						type="button"
						disabled={applying}
						onClick={() => setIdentifying(true)}
						className={`${action} bg-ink text-canvas`}
					>
						Fix match…
					</button>
					<div className="flex gap-2">
						<button
							type="button"
							onClick={() => setEditing(true)}
							className={`${action} glass flex-1 text-ink`}
						>
							Edit
						</button>
						<button
							type="button"
							disabled={refresh.isPending}
							onClick={() => refresh.mutate()}
							className={`${action} glass flex-1 text-ink`}
						>
							Refresh
						</button>
					</div>
				</div>
			)}
			{identifying && (
				<IdentifyDialog
					item={item as BaseItemDto}
					onClose={() => setIdentifying(false)}
				/>
			)}
			{editing && (
				<EditDetailsDialog
					item={item as BaseItemDto}
					onClose={() => setEditing(false)}
				/>
			)}
		</li>
	);
}

/** Every movie and show with missing metadata, so they can be fixed one after another. */
export function AttentionPage() {
	const admin = useIsAdmin();
	const query = useAttentionItems();
	const [kind, setKind] = useState<KindFilter>("all");
	const [reason, setReason] = useState<ReasonFilter>("all");

	const all = query.data ?? [];
	const shown = useMemo(
		() =>
			all.filter(
				(entry) =>
					(kind === "all" || entry.item.Type === kind) &&
					(reason === "all" || entry.reasons.includes(reason)),
			),
		[all, kind, reason],
	);
	const count = (r: ReasonFilter) =>
		all.filter((entry) => r === "all" || entry.reasons.includes(r)).length;

	return (
		<div className="content-column pb-24 pt-[calc(var(--nav-height)+2.5rem)]">
			<h1 className="text-4xl font-semibold tracking-tight sm:text-5xl">
				Attention
			</h1>
			<p className="mt-2 max-w-2xl text-sm text-ink-muted">
				Movies and shows with missing metadata: never matched to TMDb, or no
				poster or description. Fix match picks the right title; it fills in the
				rest.
			</p>

			<div className="mt-5 flex flex-wrap gap-2">
				{(["all", "Movie", "Series"] as const).map((key) => (
					<button
						key={key}
						type="button"
						onClick={() => setKind(key)}
						className={`${pill} ${kind === key ? pillOn : ""}`}
					>
						{key === "all"
							? "Movies and shows"
							: key === "Movie"
								? "Movies"
								: "Shows"}
					</button>
				))}
				<span className="mx-1 w-px self-stretch bg-glass-border" />
				{(["all", "match", "poster", "description"] as const).map((key) => (
					<button
						key={key}
						type="button"
						onClick={() => setReason(key)}
						className={`${pill} ${reason === key ? pillOn : ""}`}
					>
						{key === "all" ? "Everything" : REASON_LABEL[key]}{" "}
						<span className="opacity-60">{query.data ? count(key) : ""}</span>
					</button>
				))}
			</div>

			<div className="mt-6">
				{query.isPending && (
					<p className="text-sm text-ink-muted">Checking your library…</p>
				)}
				{query.isError && (
					<p className="text-sm text-ink-muted">Couldn't load your library.</p>
				)}
				{query.data && shown.length === 0 && (
					<p className="text-sm text-ink-muted">
						{all.length === 0
							? "Nothing needs attention. Every title has a match, a poster and a description."
							: "Nothing matches these filters."}
					</p>
				)}
				{shown.length > 0 && (
					<>
						<p className="mb-3 text-sm text-ink-faint">
							{shown.length} {shown.length === 1 ? "title" : "titles"}
						</p>
						<ul className="flex flex-col gap-3">
							{shown.map((entry) => (
								<Row
									key={entry.item.Id}
									entry={entry}
									canFix={admin.data === true}
								/>
							))}
						</ul>
					</>
				)}
			</div>
		</div>
	);
}
