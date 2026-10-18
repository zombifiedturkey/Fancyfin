import { type Direction, pickNext } from "./spatial-nav";

const KEYS: Record<string, Direction> = {
	ArrowUp: "up",
	ArrowDown: "down",
	ArrowLeft: "left",
	ArrowRight: "right",
};

const FOCUSABLE = [
	"a[href]",
	"button",
	"input",
	"select",
	"textarea",
	'[role="button"]',
	'[role="menuitem"]',
	'[tabindex]:not([tabindex="-1"])',
].join(",");

function isTextField(element: Element): boolean {
	if (element instanceof HTMLTextAreaElement) return true;
	if (!(element instanceof HTMLInputElement)) return false;
	return !["button", "checkbox", "radio", "range", "submit"].includes(
		element.type,
	);
}

function isUsable(element: HTMLElement): boolean {
	if (element.hasAttribute("disabled")) return false;
	if (element.closest('[aria-hidden="true"], [inert]')) return false;
	const box = element.getBoundingClientRect();
	if (box.width <= 0 || box.height <= 0) return false;
	return element.checkVisibility?.({ checkVisibilityCSS: true }) ?? true;
}

/** Only what's inside the front-most dialog can be reached while one is open. */
function searchRoot(): ParentNode {
	const dialogs = document.querySelectorAll(
		'[role="dialog"], [aria-modal="true"]',
	);
	return dialogs.length > 0
		? (dialogs[dialogs.length - 1] ?? document)
		: document;
}

function focusElement(element: HTMLElement) {
	element.focus({ preventScroll: true });
	element.scrollIntoView({
		block: "center",
		inline: "center",
		behavior: "smooth",
	});
}

function onKeyDown(event: KeyboardEvent) {
	const direction = KEYS[event.key];
	if (!direction || event.defaultPrevented) return;
	if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
	// The player uses the arrows itself (seek, volume).
	if (document.querySelector("video")) return;

	const active = document.activeElement;
	// Left and right move the cursor while typing.
	if (
		active &&
		isTextField(active) &&
		(direction === "left" || direction === "right")
	) {
		return;
	}

	const candidates = Array.from(
		searchRoot().querySelectorAll<HTMLElement>(FOCUSABLE),
	).filter(isUsable);
	if (candidates.length === 0) return;
	event.preventDefault();

	const current =
		active instanceof HTMLElement && candidates.includes(active)
			? active
			: undefined;

	if (!current) {
		// Nothing focused yet: start with whatever is nearest the top left of the screen.
		const first = candidates
			.map((element) => ({ element, box: element.getBoundingClientRect() }))
			.filter(({ box }) => box.bottom > 0 && box.top < window.innerHeight)
			.sort(
				(a, b) => a.box.top * 2 + a.box.left - (b.box.top * 2 + b.box.left),
			)[0];
		if (first) focusElement(first.element);
		return;
	}

	const others = candidates.filter((element) => element !== current);
	const index = pickNext(
		current.getBoundingClientRect(),
		others.map((element) => element.getBoundingClientRect()),
		direction,
	);
	const target = others[index];
	if (target) {
		focusElement(target);
	} else if (direction === "up" || direction === "down") {
		// Nothing further that way: let the page scroll instead.
		window.scrollBy({
			top: direction === "down" ? 320 : -320,
			behavior: "smooth",
		});
	}
}

/** The Android TV app's Back button: close an open dialog first, and say whether that was done. */
function onBack(): boolean {
	if (!document.querySelector('[role="dialog"]')) return false;
	window.dispatchEvent(
		new KeyboardEvent("keydown", { key: "Escape", bubbles: true }),
	);
	return true;
}

/** Turn on arrow-key navigation between buttons and links, for a TV remote. */
export function installSpatialNav(): void {
	window.addEventListener("keydown", onKeyDown);
	window.fancyfinBack = onBack;
}
