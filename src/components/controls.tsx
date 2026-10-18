import { type ReactNode, useId } from "react";

/** A row of mutually exclusive choices, shown as pills. */
export function Segmented<T extends string>({
	label,
	value,
	options,
	onChange,
}: {
	label: string;
	value: T;
	options: readonly { value: T; label: string }[];
	onChange: (value: T) => void;
}) {
	return (
		<fieldset className="flex min-w-0 flex-wrap gap-2">
			<legend className="sr-only">{label}</legend>
			{options.map((option) => (
				<button
					key={option.value}
					type="button"
					aria-pressed={option.value === value}
					onClick={() => onChange(option.value)}
					className={`cursor-pointer rounded-pill px-4 py-1.5 text-sm font-medium transition-colors ${
						option.value === value
							? "bg-ink text-canvas"
							: "glass text-ink-muted hover:text-ink"
					}`}
				>
					{option.label}
				</button>
			))}
		</fieldset>
	);
}

/** An on/off switch with its label beside it. */
export function Switch({
	checked,
	onChange,
	children,
	className = "",
}: {
	checked: boolean;
	onChange: (checked: boolean) => void;
	children: ReactNode;
	className?: string;
}) {
	const id = useId();
	return (
		<span className={`inline-flex items-center gap-3 ${className}`}>
			<button
				id={id}
				type="button"
				role="switch"
				aria-checked={checked}
				onClick={() => onChange(!checked)}
				className={`relative h-6 w-11 shrink-0 cursor-pointer rounded-full border border-glass-border transition-colors ${
					checked ? "bg-accent" : "bg-glass-strong"
				}`}
			>
				<span
					aria-hidden
					className={`absolute left-0.5 top-0.5 size-5 rounded-full bg-white shadow transition-transform ${
						checked ? "translate-x-5" : "translate-x-0"
					}`}
				/>
			</button>
			<label htmlFor={id} className="cursor-pointer text-sm">
				{children}
			</label>
		</span>
	);
}
