import { apiUrl } from "./api-base";

export type PublicServerInfo = {
	ServerName: string;
	Version: string;
	Id: string;
	LocalAddress?: string;
	StartupWizardCompleted?: boolean;
};

/** Unauthenticated endpoint, so we can check the connection before any login exists. */
export async function fetchPublicServerInfo(
	signal?: AbortSignal,
): Promise<PublicServerInfo> {
	const res = await fetch(apiUrl("/System/Info/Public"), { signal });
	if (!res.ok) {
		throw new Error(`Server responded ${res.status} ${res.statusText}`);
	}
	return (await res.json()) as PublicServerInfo;
}
