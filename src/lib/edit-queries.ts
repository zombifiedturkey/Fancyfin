import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import {
	keepPreviousData,
	useMutation,
	useQuery,
	useQueryClient,
} from "@tanstack/react-query";
import { apiUrl } from "./api-base";
import { api } from "./jellyfin";

/** Pictures a title can have. "Primary" is the poster. */
export type ImageType = "Primary" | "Backdrop" | "Logo" | "Thumb";

export type ItemImage = {
	ImageType?: string;
	ImageIndex?: number;
	ImageTag?: string;
	Width?: number;
	Height?: number;
};

export type RemoteImage = {
	ProviderName?: string;
	Url?: string;
	ThumbnailUrl?: string;
	Width?: number;
	Height?: number;
	Language?: string;
	CommunityRating?: number;
};

type RemoteImageResult = {
	Images?: RemoteImage[];
	TotalRecordCount?: number;
};

export class HttpError extends Error {
	constructor(readonly status: number) {
		super(`HTTP ${status}`);
	}
}

/** A small authenticated request. The token goes in the query string, as it does for images and video. */
async function request(path: string, init?: RequestInit): Promise<Response> {
	const joiner = path.includes("?") ? "&" : "?";
	const url = apiUrl(
		`${path}${joiner}api_key=${encodeURIComponent(api.accessToken)}`,
	);
	const response = await fetch(url, init);
	if (!response.ok) throw new HttpError(response.status);
	return response;
}

/** What went wrong, in words a person can act on. */
export function describeError(error: unknown): string {
	if (error instanceof HttpError) {
		if (error.status === 401 || error.status === 403) {
			return "You don't have permission to change this title.";
		}
		if (error.status === 400 || error.status === 404) {
			return "The server didn't accept that. If it came from an online provider, try a different one.";
		}
	}
	return "Couldn't reach the server. Try again.";
}

/** The pictures a title has right now. */
export function useItemImages(itemId: string) {
	return useQuery({
		queryKey: ["item-images", itemId],
		queryFn: async (): Promise<ItemImage[]> => {
			const response = await request(`/Items/${itemId}/Images`);
			return (await response.json()) as ItemImage[];
		},
	});
}

/** Pictures the metadata providers (TMDb and so on) have on offer. Asks for `limit` of them. */
export function useRemoteImages(
	itemId: string,
	type: ImageType,
	allLanguages: boolean,
	limit: number,
) {
	return useQuery({
		queryKey: ["remote-images", itemId, type, allLanguages, limit],
		placeholderData: keepPreviousData,
		staleTime: 5 * 60_000,
		retry: false,
		queryFn: async (): Promise<{ images: RemoteImage[]; total: number }> => {
			const params = new URLSearchParams({
				type,
				limit: String(limit),
				includeAllLanguages: String(allLanguages),
			});
			const response = await request(
				`/Items/${itemId}/RemoteImages?${params.toString()}`,
			);
			const data = (await response.json()) as RemoteImageResult;
			const images = (data.Images ?? []).filter((image) => image.Url);
			return { images, total: data.TotalRecordCount ?? images.length };
		},
	});
}

async function fileToBase64(file: File): Promise<string> {
	const bytes = new Uint8Array(await file.arrayBuffer());
	let binary = "";
	for (let i = 0; i < bytes.length; i += 0x8000) {
		binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
	}
	return btoa(binary);
}

function useAfterChange() {
	const queryClient = useQueryClient();
	return () => queryClient.invalidateQueries();
}

/** Download one of the provider's pictures onto the title. */
export function useUseRemoteImage(itemId: string) {
	const afterChange = useAfterChange();
	return useMutation({
		mutationFn: async (input: { type: ImageType; image: RemoteImage }) => {
			const params = new URLSearchParams({
				type: input.type,
				imageUrl: input.image.Url ?? "",
			});
			if (input.image.ProviderName) {
				params.set("providerName", input.image.ProviderName);
			}
			await request(`/Items/${itemId}/RemoteImages/Download?${params}`, {
				method: "POST",
			});
		},
		onSuccess: afterChange,
	});
}

/** Upload a picture from this computer. The server expects it base64 encoded. */
export function useUploadImage(itemId: string) {
	const afterChange = useAfterChange();
	return useMutation({
		mutationFn: async (input: { type: ImageType; file: File }) => {
			await request(`/Items/${itemId}/Images/${input.type}`, {
				method: "POST",
				headers: { "Content-Type": input.file.type },
				body: await fileToBase64(input.file),
			});
		},
		onSuccess: afterChange,
	});
}

/** Remove one picture. */
export function useDeleteImage(itemId: string) {
	const afterChange = useAfterChange();
	return useMutation({
		mutationFn: async (input: { type: ImageType; index: number }) => {
			await request(`/Items/${itemId}/Images/${input.type}/${input.index}`, {
				method: "DELETE",
			});
		},
		onSuccess: afterChange,
	});
}

/** Save changed details. Sends the whole item back, as the Jellyfin web editor does. */
export function useUpdateItem(itemId: string) {
	const afterChange = useAfterChange();
	return useMutation({
		mutationFn: async (item: BaseItemDto) => {
			await request(`/Items/${itemId}`, {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify(item),
			});
		},
		onSuccess: afterChange,
	});
}

/** Rating names (PG, M, R18+ and so on) to suggest while typing. Optional, so failures are ignored. */
export function useRatingNames() {
	return useQuery({
		queryKey: ["parental-ratings"],
		staleTime: 60 * 60_000,
		retry: false,
		queryFn: async (): Promise<string[]> => {
			const response = await request("/Localization/ParentalRatings");
			const data = (await response.json()) as { Name?: string }[];
			return [
				...new Set(data.map((entry) => entry.Name ?? "").filter(Boolean)),
			];
		},
	});
}
