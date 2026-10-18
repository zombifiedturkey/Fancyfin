import { type FormEvent, useState } from "react";
import {
	ARR_LABEL,
	type ArrApp,
	type ArrConnection,
	connectionDetail,
} from "../lib/arr";
import { saveArrPrefs, useArrPrefs } from "../lib/arr-prefs";
import {
	describeArrError,
	useArrStatus,
	useConnectArr,
	useDisconnectArr,
} from "../lib/arr-queries";
import { CheckField } from "./controls";

const pill =
	"glass cursor-pointer rounded-pill px-5 py-2 text-sm font-semibold text-ink transition-colors hover:bg-glass-strong disabled:cursor-default disabled:opacity-50";

const field =
	"glass mt-1.5 w-full rounded-card px-4 py-3 text-sm text-ink outline-none placeholder:text-ink-faint focus-visible:border-accent";

const labelClass =
	"text-[11px] font-semibold uppercase tracking-[0.16em] text-ink-faint";

const EXAMPLE: Record<ArrApp, string> = {
	radarr: "http://192.168.1.20:7878",
	sonarr: "http://192.168.1.20:8989",
};

function Icon({ app }: { app: ArrApp }) {
	return (
		<svg
			aria-hidden
			viewBox="0 0 24 24"
			className="size-6"
			fill="none"
			stroke="currentColor"
			strokeWidth="2"
			strokeLinecap="round"
			strokeLinejoin="round"
		>
			{app === "radarr" ? (
				<>
					<rect x="3" y="6" width="18" height="14" rx="2" />
					<path d="M3 10h18M7 6l3 4M13 6l3 4" />
				</>
			) : (
				<>
					<rect x="3" y="7" width="18" height="13" rx="2" />
					<path d="M8 3l4 4 4-4" />
				</>
			)}
		</svg>
	);
}

function ArrCard({
	app,
	connection,
}: {
	app: ArrApp;
	connection: ArrConnection;
}) {
	const label = ARR_LABEL[app];
	const prefs = useArrPrefs()[app];
	const connect = useConnectArr(app);
	const disconnect = useDisconnectArr(app);

	const [editing, setEditing] = useState(false);
	const [url, setUrl] = useState("");
	const [key, setKey] = useState("");
	const [confirming, setConfirming] = useState(false);

	const detail = connectionDetail(app, connection);

	const startEdit = () => {
		setUrl(connection.url ?? "");
		setKey("");
		connect.reset();
		setEditing(true);
	};

	const save = (event: FormEvent) => {
		event.preventDefault();
		connect.mutate(
			{ url, key },
			{
				onSuccess: () => {
					setEditing(false);
					setKey("");
				},
			},
		);
	};

	return (
		<div className="glass rounded-card p-4">
			<div className="flex flex-wrap items-center gap-4">
				<span
					className={`flex size-11 shrink-0 items-center justify-center rounded-xl ${
						app === "radarr"
							? "bg-amber-400 text-amber-950"
							: "bg-sky-400 text-sky-950"
					}`}
				>
					<Icon app={app} />
				</span>
				<div className="min-w-0 flex-1 basis-56">
					<p className="text-base font-semibold">{label}</p>
					<p className="text-sm text-ink-muted">
						{connection.configured ? (
							<>
								Connected to{" "}
								<strong className="break-all font-semibold text-ink">
									{connection.url}
								</strong>
								{detail && ` · ${detail}`}
							</>
						) : (
							"Not connected"
						)}
					</p>
					{connection.error && (
						<p role="alert" className="mt-1 text-sm text-danger">
							{connection.error}
						</p>
					)}
				</div>
				<div className="flex flex-wrap gap-2">
					<button type="button" onClick={startEdit} className={pill}>
						{connection.configured ? "Edit" : "Connect"}
					</button>
					{connection.configured && (
						<button
							type="button"
							disabled={disconnect.isPending}
							onClick={() => {
								if (!confirming) {
									setConfirming(true);
									window.setTimeout(() => setConfirming(false), 4000);
									return;
								}
								setConfirming(false);
								setEditing(false);
								disconnect.mutate();
							}}
							className={pill}
						>
							{confirming ? "Click again to disconnect" : "Disconnect"}
						</button>
					)}
				</div>
			</div>

			{disconnect.error && (
				<p role="alert" className="mt-3 text-sm text-danger">
					{describeArrError(disconnect.error)}
				</p>
			)}

			{editing && (
				<form onSubmit={save} className="mt-5 space-y-4">
					<label className="block">
						<span className={labelClass}>Address</span>
						<input
							type="text"
							value={url}
							onChange={(event) => setUrl(event.target.value)}
							placeholder={EXAMPLE[app]}
							autoComplete="off"
							spellCheck={false}
							className={field}
						/>
					</label>
					<label className="block">
						<span className={labelClass}>API key</span>
						<input
							type="password"
							value={key}
							onChange={(event) => setKey(event.target.value)}
							placeholder={
								connection.configured
									? "Leave blank to keep the saved key"
									: `From ${label}: Settings, General`
							}
							autoComplete="off"
							spellCheck={false}
							className={field}
						/>
					</label>
					<p className="text-xs text-ink-faint">
						Saved on your Fancyfin server and never sent back to the browser.
						Fancyfin checks the connection before saving.
					</p>
					{connect.error && (
						<p role="alert" className="text-sm text-danger">
							{describeArrError(connect.error)}
						</p>
					)}
					<div className="flex flex-wrap gap-2">
						<button
							type="submit"
							disabled={connect.isPending || !url.trim()}
							className="cursor-pointer rounded-pill bg-ink px-5 py-2 text-sm font-semibold text-canvas transition-opacity hover:opacity-90 disabled:cursor-default disabled:opacity-40"
						>
							{connect.isPending ? "Checking…" : "Save"}
						</button>
						<button
							type="button"
							onClick={() => setEditing(false)}
							className={pill}
						>
							Cancel
						</button>
					</div>
				</form>
			)}

			{connection.configured && (
				<div className="mt-4">
					<CheckField
						checked={prefs.skip}
						onChange={(skip) => saveArrPrefs(app, { ...prefs, skip })}
					>
						One-click add: skip the options window and use my last settings
					</CheckField>
				</div>
			)}
		</div>
	);
}

/** Connect Radarr and Sonarr. Until they are connected and working, no Add buttons appear. */
export function ArrSettings() {
	const status = useArrStatus();

	if (status.isPending) {
		return <div aria-hidden className="shimmer h-24 rounded-card" />;
	}
	if (status.isError) {
		return (
			<p className="text-sm text-ink-muted">
				Not available on this server. Radarr and Sonarr need the latest Fancyfin
				image, which includes the small service that keeps their connection.
			</p>
		);
	}

	return (
		<>
			<p className="-mt-1 text-sm text-ink-muted">
				Connect them and titles you don't have get an Add button. Until they are
				connected and working, the buttons stay hidden.
			</p>
			{!status.data.persistent && (
				<p className="text-sm text-danger">
					Fancyfin has no folder to save this in, so it will be forgotten when
					the container is updated. Map a path to <code>/config</code> on the
					container.
				</p>
			)}
			<ArrCard app="radarr" connection={status.data.apps.radarr} />
			<ArrCard app="sonarr" connection={status.data.apps.sonarr} />
		</>
	);
}
