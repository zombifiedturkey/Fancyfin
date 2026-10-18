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

/** A labelled drop-down in the glass style. Give it <option> elements. */
export function SelectField({
	label,
	value,
	onChange,
	children,
	disabled = false,
}: {
	label: string;
	value: string;
	onChange: (value: string) => void;
	children: ReactNode;
	disabled?: boolean;
}) {
	return (
		<label className="block min-w-0">
			<span className="text-[11px] font-semibold uppercase tracking-[0.16em] text-ink-faint">
				{label}
			</span>
			<span className="relative mt-1.5 block">
				<select
					value={value}
					disabled={disabled}
					onChange={(event) => onChange(event.target.value)}
					className="glass w-full cursor-pointer appearance-none truncate rounded-card py-3 pl-4 pr-10 text-sm text-ink outline-none transition-colors hover:bg-glass-strong disabled:cursor-default disabled:opacity-50 [&>option]:bg-canvas-raised [&>option]:text-ink"
				>
					{children}
				</select>
				<svg
					aria-hidden
					viewBox="0 0 24 24"
					className="pointer-events-none absolute right-4 top-1/2 size-4 -translate-y-1/2 text-ink-muted"
					fill="none"
					stroke="currentColor"
					strokeWidth="2.5"
					strokeLinecap="round"
					strokeLinejoin="round"
				>
					<path d="M6 9l6 6 6-6" />
				</svg>
			</span>
		</label>
	);
}

/** A tick box with its label beside it. */
export function CheckField({
	checked,
	onChange,
	children,
}: {
	checked: boolean;
	onChange: (checked: boolean) => void;
	children: ReactNode;
}) {
	return (
		<label className="flex cursor-pointer items-center gap-3 text-sm">
			<input
				type="checkbox"
				checked={checked}
				onChange={(event) => onChange(event.target.checked)}
				className="size-4 shrink-0 cursor-pointer accent-(--color-accent)"
			/>
			<span>{children}</span>
		</label>
	);
}
