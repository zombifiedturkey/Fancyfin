import {
	type ReactNode,
	useCallback,
	useEffect,
	useRef,
	useState,
} from "react";

type ShelfProps = {
	title: string;
	/** Something to show at the right of the heading, such as a "See all" link. */
	action?: ReactNode;
	/** Extra controls under the heading, such as season tabs. */
	toolbar?: ReactNode;
	/** The row's items: <li> elements. */
	children: ReactNode;
};

function Chevron({ direction }: { direction: "left" | "right" }) {
	return (
		<svg
			aria-hidden
			viewBox="0 0 24 24"
			className="size-5"
			fill="none"
			stroke="currentColor"
			strokeWidth="2.5"
			strokeLinecap="round"
			strokeLinejoin="round"
		>
			<path d={direction === "left" ? "M15 5l-7 7 7 7" : "M9 5l7 7-7 7"} />
		</svg>
	);
}

const arrowButton =
	"glass pointer-events-auto flex size-11 cursor-pointer items-center justify-center rounded-full text-ink shadow-card transition-transform hover:scale-110";

/** A titled horizontal row. It starts on the content edge and scrolls off the right edge. */
export function Shelf({ title, action, toolbar, children }: ShelfProps) {
	const scroller = useRef<HTMLDivElement>(null);
	const [edges, setEdges] = useState({ left: false, right: false });

	// Work out whether there is more to scroll to on either side.
	const updateEdges = useCallback(() => {
		const element = scroller.current;
		if (!element) return;
		const left = element.scrollLeft > 4;
		const right =
			element.scrollLeft + element.clientWidth < element.scrollWidth - 4;
		setEdges((prev) =>
			prev.left === left && prev.right === right ? prev : { left, right },
		);
	}, []);

	useEffect(() => {
		const element = scroller.current;
		if (!element) return;
		updateEdges();
		const observer = new ResizeObserver(updateEdges);
		observer.observe(element);
		if (element.firstElementChild) observer.observe(element.firstElementChild);
		return () => observer.disconnect();
	}, [updateEdges]);

	const scrollByPage = (direction: -1 | 1) => {
		const element = scroller.current;
		if (!element) return;
		const reduceMotion = window.matchMedia(
			"(prefers-reduced-motion: reduce)",
		).matches;
		element.scrollBy({
			left: direction * element.clientWidth * 0.85,
			behavior: reduceMotion ? "auto" : "smooth",
		});
	};

	return (
		<section aria-label={title} className="mt-6 first:mt-0">
			<div className="content-column flex items-baseline justify-between gap-4">
				<h2 className="text-xl font-semibold tracking-tight">{title}</h2>
				{action}
			</div>
			{toolbar && <div className="content-column mt-4">{toolbar}</div>}

			<div className="group/shelf relative">
				{/* Vertical padding leaves room for the hover scale and shadow so they are not clipped. */}
				<div
					ref={scroller}
					onScroll={updateEdges}
					className="hide-scrollbar content-inset overflow-x-auto py-6"
				>
					<ul className="flex w-max gap-4 sm:gap-5">{children}</ul>
				</div>

				{/* Arrows sit over the artwork area at each edge. They appear when you hover the row
				    and are left out on touch screens, where swiping already works. */}
				{edges.left && (
					<div className="pointer-events-none absolute inset-y-6 bottom-16 left-0 flex w-20 items-center justify-start bg-linear-to-r from-canvas/70 to-transparent pl-3 opacity-0 transition-opacity group-focus-within/shelf:opacity-100 group-hover/shelf:opacity-100 [@media(hover:none)]:hidden">
						<button
							type="button"
							aria-label={`Scroll ${title} left`}
							onClick={() => scrollByPage(-1)}
							className={arrowButton}
						>
							<Chevron direction="left" />
						</button>
					</div>
				)}
				{edges.right && (
					<div className="pointer-events-none absolute inset-y-6 bottom-16 right-0 flex w-20 items-center justify-end bg-linear-to-l from-canvas/70 to-transparent pr-3 opacity-0 transition-opacity group-focus-within/shelf:opacity-100 group-hover/shelf:opacity-100 [@media(hover:none)]:hidden">
						<button
							type="button"
							aria-label={`Scroll ${title} right`}
							onClick={() => scrollByPage(1)}
							className={arrowButton}
						>
							<Chevron direction="right" />
						</button>
					</div>
				)}
			</div>
		</section>
	);
}
