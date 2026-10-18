/** A stable 0-359 hue derived from a string, so the same name always gets the same colour. */
export function hueFor(text: string): number {
	let hue = 0;
	for (const char of text) hue = (hue * 31 + (char.codePointAt(0) ?? 0)) % 360;
	return hue;
}
