/** The HTTP status carried by an Axios-style error (the SDK uses Axios), if there is one. */
export function getHttpStatus(error: unknown): number | undefined {
	if (typeof error !== "object" || error === null) return undefined;
	const response = (error as { response?: { status?: unknown } }).response;
	return typeof response?.status === "number" ? response.status : undefined;
}
