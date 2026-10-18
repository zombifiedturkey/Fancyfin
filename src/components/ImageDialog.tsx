import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import { useRef, useState } from "react";
import {
	describeError,
	type ImageType,
	type RemoteImage,
	useDeleteImage,
	useItemImages,
	useRemoteImages,
	useUploadImage,
	useUseRemoteImage,
} from "../lib/edit-queries";
import { imageUrl } from "../lib/images";
import { FadeImage } from "./FadeImage";
import { Modal } from "./Modal";

const KINDS: {
	type: ImageType;
	label: string;
	frame: string;
	grid: string;
	fit: string;
}[] = [
	{
		type: "Primary",
		label: "Poster",
		frame: "aspect-[2/3]",
		grid: "grid-cols-3 sm:grid-cols-4",
		fit: "object-cover",
	},
	{
		type: "Backdrop",
		label: "Backdrop",
		frame: "aspect-video",
		grid: "grid-cols-2 sm:grid-cols-3",
		fit: "object-cover",
	},
	{
		type: "Logo",
		label: "Logo",
		frame: "aspect-[16/7]",
		grid: "grid-cols-2 sm:grid-cols-3",
		fit: "object-contain p-3",
	},
	{
		type: "Thumb",
		label: "Thumbnail",
		frame: "aspect-video",
		grid: "grid-cols-2 sm:grid-cols-3",
		fit: "object-cover",
	},
];

const PAGE = 24;
const MAX_UPLOAD_BYTES = 15 * 1024 * 1024;
const ACCEPTED = ["image/jpeg", "image/png", "image/webp", "image/gif"];

const button =
	"cursor-pointer rounded-pill px-4 py-2 text-sm font-semibold transition-colors disabled:cursor-wait disabled:opacity-60";

function Frame({
	frame,
	children,
}: {
	frame: string;
	children: React.ReactNode;
}) {
	return (
		<div
			className={`relative ${frame} overflow-hidden rounded-lg border border-glass-border bg-glass-strong`}
		>
			{children}
		</div>
	);
}

function describe(image: RemoteImage): string {
	return [
		image.Width && image.Height ? `${image.Width}×${image.Height}` : undefined,
		image.Language?.toUpperCase(),
		image.ProviderName,
	]
		.filter(Boolean)
		.join(" · ");
}

/** Change a title's poster, backdrop, logo or thumbnail: pick one from the providers, upload your own, or remove one. */
export function ImageDialog({
	item,
	onClose,
}: {
	item: BaseItemDto;
	onClose: () => void;
}) {
	const itemId = item.Id ?? "";
	const [type, setType] = useState<ImageType>("Primary");
	const [allLanguages, setAllLanguages] = useState(false);
	const [limit, setLimit] = useState(PAGE);
	const [pending, setPending] = useState<RemoteImage | undefined>();
	const [message, setMessage] = useState<string | undefined>();
	const fileInput = useRef<HTMLInputElement>(null);

	const kind = KINDS.find((entry) => entry.type === type) ?? KINDS[0];
	const current = useItemImages(itemId);
	const remote = useRemoteImages(itemId, type, allLanguages, limit);
	const choose = useUseRemoteImage(itemId);
	const upload = useUploadImage(itemId);
	const remove = useDeleteImage(itemId);
	const busy = choose.isPending || upload.isPending || remove.isPending;
	const failure = choose.error ?? upload.error ?? remove.error;

	if (!kind) return null;

	const mine = (current.data ?? []).filter((image) => image.ImageType === type);

	const switchType = (next: ImageType) => {
		setType(next);
		setLimit(PAGE);
		setPending(undefined);
		setMessage(undefined);
		choose.reset();
		upload.reset();
		remove.reset();
	};

	const onFile = (file: File | undefined) => {
		if (!file) return;
		setMessage(undefined);
		if (!ACCEPTED.includes(file.type)) {
			setMessage("Choose a JPG, PNG, WebP or GIF picture.");
			return;
		}
		if (file.size > MAX_UPLOAD_BYTES) {
			setMessage("That picture is over 15 MB. Choose a smaller one.");
			return;
		}
		upload.mutate({ type, file });
	};

	return (
		<Modal
			title={`Artwork · ${item.Name ?? "title"}`}
			onClose={onClose}
			size="lg"
		>
			<div role="tablist" aria-label="Kind of artwork" className="flex gap-2">
				{KINDS.map((entry) => (
					<button
						key={entry.type}
						type="button"
						role="tab"
						aria-selected={entry.type === type}
						onClick={() => switchType(entry.type)}
						className={`${button} ${
							entry.type === type ? "bg-ink text-canvas" : "glass text-ink"
						}`}
					>
						{entry.label}
					</button>
				))}
			</div>

			{(failure || message) && (
				<p role="alert" className="mt-4 text-sm text-danger">
					{message ?? describeError(failure)}
				</p>
			)}

			<section className="mt-6" aria-label="Current artwork">
				<div className="flex items-center justify-between gap-4">
					<h3 className="text-sm font-semibold text-ink-muted">Current</h3>
					<div>
						<input
							ref={fileInput}
							type="file"
							accept={ACCEPTED.join(",")}
							className="hidden"
							onChange={(event) => {
								onFile(event.target.files?.[0]);
								event.target.value = "";
							}}
						/>
						<button
							type="button"
							disabled={busy}
							onClick={() => fileInput.current?.click()}
							className={`${button} glass text-ink`}
						>
							{upload.isPending ? "Uploading…" : "Upload your own…"}
						</button>
					</div>
				</div>
				{mine.length === 0 ? (
					<p className="mt-3 text-sm text-ink-muted">
						No {kind.label.toLowerCase()} yet.
					</p>
				) : (
					<ul className={`mt-3 grid gap-3 ${kind.grid}`}>
						{mine.map((image) => {
							const index = image.ImageIndex ?? 0;
							return (
								<li key={`${image.ImageType}-${index}-${image.ImageTag}`}>
									<Frame frame={kind.frame}>
										<FadeImage
											src={imageUrl(itemId, type, {
												tag: image.ImageTag,
												maxWidth: 400,
												index,
											})}
											className={`absolute inset-0 size-full ${kind.fit}`}
										/>
									</Frame>
									<button
										type="button"
										disabled={busy}
										onClick={() => remove.mutate({ type, index })}
										className="mt-2 cursor-pointer text-xs font-medium text-danger transition-opacity hover:opacity-80 disabled:cursor-wait disabled:opacity-60"
									>
										Remove
									</button>
								</li>
							);
						})}
					</ul>
				)}
			</section>

			<section className="mt-8" aria-label="From online providers">
				<div className="flex flex-wrap items-center justify-between gap-3">
					<h3 className="text-sm font-semibold text-ink-muted">
						From online providers
					</h3>
					<label className="flex cursor-pointer items-center gap-2 text-sm">
						<input
							type="checkbox"
							checked={allLanguages}
							onChange={(event) => {
								setAllLanguages(event.target.checked);
								setLimit(PAGE);
							}}
							className="size-4 accent-[var(--color-accent)]"
						/>
						Include all languages
					</label>
				</div>

				{pending && (
					<div className="mt-4 flex flex-wrap items-center gap-3 rounded-card border border-glass-border bg-glass p-3 text-sm">
						<span className="min-w-0 flex-1 text-ink-muted">
							Use this {kind.label.toLowerCase()}?
							{type === "Backdrop"
								? " It's added to the existing backdrops."
								: " It replaces the current one."}
						</span>
						<button
							type="button"
							disabled={busy}
							onClick={() => setPending(undefined)}
							className={`${button} glass text-ink`}
						>
							Cancel
						</button>
						<button
							type="button"
							disabled={busy}
							onClick={() =>
								choose.mutate(
									{ type, image: pending },
									{ onSuccess: () => setPending(undefined) },
								)
							}
							className={`${button} bg-ink text-canvas`}
						>
							{choose.isPending ? "Downloading…" : "Use it"}
						</button>
					</div>
				)}

				<div className="mt-3">
					{remote.isPending ? (
						<ul className={`grid gap-3 ${kind.grid}`} aria-hidden>
							{Array.from({ length: 8 }, (_, i) => (
								<li key={i} className={`shimmer ${kind.frame} rounded-lg`} />
							))}
						</ul>
					) : remote.isError ? (
						<p className="text-sm text-ink-muted">
							Couldn't get pictures from the providers. Check that an image
							provider (such as TMDb) is turned on in your Jellyfin library
							settings.
						</p>
					) : (remote.data?.images ?? []).length === 0 ? (
						<p className="text-sm text-ink-muted">
							The providers have no {kind.label.toLowerCase()} for this title.
						</p>
					) : (
						<>
							<ul className={`grid gap-3 ${kind.grid}`}>
								{(remote.data?.images ?? []).map((image) => (
									<li key={image.Url}>
										<button
											type="button"
											disabled={busy}
											onClick={() => setPending(image)}
											aria-label={`Use this ${kind.label.toLowerCase()}, ${describe(image)}`}
											className={`block w-full cursor-pointer rounded-lg text-left outline-offset-2 transition-transform hover:scale-[1.02] disabled:cursor-wait ${
												pending?.Url === image.Url ? "ring-2 ring-accent" : ""
											}`}
										>
											<Frame frame={kind.frame}>
												<FadeImage
													key={image.Url}
													src={image.ThumbnailUrl ?? image.Url ?? ""}
													className={`absolute inset-0 size-full ${kind.fit}`}
												/>
											</Frame>
										</button>
										<p className="mt-1 truncate text-xs text-ink-faint">
											{describe(image)}
										</p>
									</li>
								))}
							</ul>
							{(remote.data?.images.length ?? 0) <
								(remote.data?.total ?? 0) && (
								<button
									type="button"
									disabled={remote.isFetching}
									onClick={() => setLimit((value) => value + PAGE)}
									className={`${button} glass mt-5 text-ink`}
								>
									{remote.isFetching ? "Loading…" : "Show more"}
								</button>
							)}
						</>
					)}
				</div>
			</section>
		</Modal>
	);
}
