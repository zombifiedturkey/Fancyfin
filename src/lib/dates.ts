/** "12 Mar 2021" in the viewer's own locale. Returns undefined for missing or invalid dates. */
export function formatDate(iso: string | null | undefined): string | undefined {
	if (!iso) return undefined;
	const date = new Date(iso);
	if (Number.isNaN(date.getTime())) return undefined;
	return date.toLocaleDateString(undefined, {
		day: "numeric",
		month: "short",
		year: "numeric",
	});
}
