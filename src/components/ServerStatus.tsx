import { useQuery } from "@tanstack/react-query";
import { fetchPublicServerInfo } from "../lib/server-info";

/** Shows whether the Jellyfin server is reachable, and lets the user pick a different one. */
export function ServerStatus({
	onChangeServer,
}: {
	onChangeServer?: () => void;
}) {
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
					{error instanceof Error ? error.message : "Unknown error"}. Check the
					server address.
				</p>
			)}

			{onChangeServer && (
				<button
					type="button"
					onClick={onChangeServer}
					className="ml-auto rounded-pill px-3.5 py-1.5 text-xs font-medium text-ink-muted transition-colors hover:text-ink"
				>
					Change server
				</button>
			)}

			<button
				type="button"
				onClick={() => refetch()}
				disabled={isFetching}
				className={`${onChangeServer ? "" : "ml-auto "}rounded-pill bg-glass-strong px-3.5 py-1.5 text-xs font-medium text-ink transition-colors hover:bg-white/15 disabled:opacity-50`}
			>
				{isFetching ? "Checking…" : "Recheck"}
			</button>
		</div>
	);
}
