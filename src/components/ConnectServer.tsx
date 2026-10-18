import { type FormEvent, useState } from "react";
import { clearSession } from "../auth/session";
import {
	clearServerUrl,
	getServerUrl,
	normalizeServerUrl,
	saveServerUrl,
} from "../lib/api-base";

const EXAMPLE = "http://192.168.1.20:8096";

/** Asks for the Jellyfin server's address, checks it answers, then remembers it and reloads. */
export function ConnectServer({ onCancel }: { onCancel?: () => void }) {
	const current = getServerUrl();
	const [value, setValue] = useState(current ?? "");
	const [error, setError] = useState<string | null>(null);
	const [busy, setBusy] = useState(false);

	async function submit(event: FormEvent<HTMLFormElement>) {
		event.preventDefault();
		if (busy) return;
		const url = normalizeServerUrl(value);
		if (!url) {
			setError(`Enter the server's address, for example ${EXAMPLE}`);
			return;
		}
		if (window.location.protocol === "https:" && url.startsWith("http:")) {
			setError(
				"This page is served over HTTPS, so the browser blocks plain http:// servers. Use the server's https:// address, or open Fancyfin over http://.",
			);
			return;
		}
		setBusy(true);
		setError(null);
		try {
			const response = await fetch(`${url}/System/Info/Public`, {
				signal: AbortSignal.timeout(8000),
			});
			if (!response.ok) throw new Error(`HTTP ${response.status}`);
			const info: unknown = await response.json();
			if (
				typeof info !== "object" ||
				info === null ||
				typeof (info as { Version?: unknown }).Version !== "string"
			) {
				throw new Error("Not a Jellyfin server");
			}
			// A different server means a different login.
			clearSession();
			saveServerUrl(url);
			window.location.reload();
		} catch {
			setError(
				"Couldn't find a Jellyfin server there. Check the address and port, and that this device can reach it.",
			);
			setBusy(false);
		}
	}

	return (
		<form
			onSubmit={submit}
			className="flex w-full max-w-sm flex-col items-center gap-4"
		>
			<h1 className="text-center text-3xl font-semibold tracking-tight">
				Connect to Jellyfin
			</h1>
			<p className="mb-2 text-center text-sm text-ink-muted">
				Enter the address of your Jellyfin server, including the port.
			</p>

			<div className="w-full">
				<label htmlFor="server-url" className="sr-only">
					Server address
				</label>
				<input
					id="server-url"
					name="server-url"
					type="url"
					inputMode="url"
					autoComplete="url"
					autoCapitalize="none"
					autoCorrect="off"
					spellCheck={false}
					placeholder={EXAMPLE}
					value={value}
					onChange={(event) => setValue(event.target.value)}
					// biome-ignore lint/a11y/noAutofocus: this is the only field on the screen
					autoFocus
					className="glass w-full rounded-pill px-5 py-3 text-sm text-ink placeholder:text-ink-faint focus:bg-glass-strong"
				/>
			</div>

			{error && (
				<p role="alert" className="text-center text-sm text-danger">
					{error}
				</p>
			)}

			<button
				type="submit"
				disabled={busy || !value.trim()}
				className="mt-1 w-full rounded-pill bg-ink px-6 py-3 text-sm font-semibold text-canvas transition-opacity hover:opacity-90 disabled:opacity-50"
			>
				{busy ? "Connecting…" : "Connect"}
			</button>

			{onCancel && (
				<button
					type="button"
					onClick={onCancel}
					className="rounded-pill px-4 py-2 text-sm text-ink-muted transition-colors hover:text-ink"
				>
					Cancel
				</button>
			)}

			{current && (
				<button
					type="button"
					onClick={() => {
						clearSession();
						clearServerUrl();
						window.location.reload();
					}}
					className="rounded-pill px-4 py-2 text-xs text-ink-faint transition-colors hover:text-ink-muted"
				>
					Forget {current}
				</button>
			)}
		</form>
	);
}
