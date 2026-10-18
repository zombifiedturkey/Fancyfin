import { getTvBridge } from "../lib/tv";

const pill =
	"glass cursor-pointer rounded-pill px-5 py-2 text-sm font-semibold text-ink transition-colors hover:bg-glass-strong";

/** About the Android TV app and its own updates. Only shown inside that app. */
export function TvAppSettings() {
	const bridge = getTvBridge();
	if (!bridge) return null;

	return (
		<div className="glass rounded-card p-4">
			<p className="text-base font-semibold">Fancyfin TV {bridge.version()}</p>
			<p className="mt-1 text-sm text-ink-muted">
				The app itself updates from GitHub. Everything else updates whenever
				your Fancyfin server does.
			</p>
			<div className="mt-4 flex flex-wrap gap-2">
				<button
					type="button"
					onClick={() => bridge.checkForUpdates()}
					className={pill}
				>
					Check for updates
				</button>
				<button
					type="button"
					onClick={() => bridge.changeServer()}
					className={pill}
				>
					Change server address
				</button>
			</div>
		</div>
	);
}
