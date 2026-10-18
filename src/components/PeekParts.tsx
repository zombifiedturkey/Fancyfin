export const chip =
	"rounded-pill border border-glass-border bg-glass px-3 py-1 text-xs font-medium text-ink-muted";

export function Score({ label, value }: { label: string; value: string }) {
	return (
		<div className="glass rounded-card px-4 py-3">
			<p className="text-[11px] font-semibold uppercase tracking-wider text-ink-muted">
				{label}
			</p>
			<p className="mt-0.5 text-2xl font-semibold tracking-tight">{value}</p>
		</div>
	);
}

export function PanelSkeleton() {
	return (
		<div className="page-gutter pt-40 sm:pt-52" aria-hidden>
			<div className="flex items-end gap-8">
				<div className="shimmer aspect-[2/3] w-28 shrink-0 rounded-card sm:w-52" />
				<div className="w-full max-w-md">
					<div className="shimmer h-12 w-full rounded-card" />
					<div className="shimmer mt-4 h-5 w-2/3 rounded-pill" />
				</div>
			</div>
			<div className="shimmer mt-8 h-24 max-w-2xl rounded-card" />
		</div>
	);
}
