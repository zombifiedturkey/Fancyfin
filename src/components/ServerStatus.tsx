import { useQuery } from "@tanstack/react-query";
import { fetchPublicServerInfo } from "../lib/server-info";

/** Proves the /api proxy works end to end. Becomes part of the login screen in M1. */
export function ServerStatus() {
	const { data, error, isPending, refetch, isFetching } = useQuery({
		queryKey: ["server", "public-info"],
		queryFn: ({ signal }) => fetchPublicServerInfo(signal),
		retry: false,
	});

	return (
		<div className="glass flex flex-wrap items-center gap-x-6 gap-y-2 rounded-card px-5 py-4 text-sm">
			<div className="flex items-center gap-2">
				<span
					aria-hidden
					className={`size-2.5 rounded-full ${
						isPending
							? "animate-pulse bg-ink-faint"
							: data
								? "bg-success"
								: "bg-danger"
					}`}
				/>
				<span className="font-medium">
					{isPending
						? "Connecting to Jellyfin…"
						: data
							? "Connected"
							: "Can't reach Jellyfin"}
				</span>
			</div>

			{data && (
				<p className="text-ink-muted">
					{data.ServerName} <span className="text-ink-faint">·</span> Jellyfin{" "}
					{data.Version}
				</p>
			)}

			{error && (
				<p className="text-ink-muted">
					{error instanceof Error ? error.message : "Unknown error"}. Check{" "}
					<code className="rounded bg-glass px-1.5 py-0.5 text-xs">
						JELLYFIN_URL
					</code>{" "}
					in{" "}
					<code className="rounded bg-glass px-1.5 py-0.5 text-xs">
						.env.local
					</code>{" "}
					and restart the dev server.
				</p>
			)}

			<button
				type="button"
				onClick={() => refetch()}
				disabled={isFetching}
				className="ml-auto rounded-pill bg-glass-strong px-3.5 py-1.5 text-xs font-medium text-ink transition-colors hover:bg-white/15 disabled:opacity-50"
			>
				{isFetching ? "Checking…" : "Recheck"}
			</button>
		</div>
	);
}
