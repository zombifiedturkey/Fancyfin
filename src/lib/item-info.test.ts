import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import { describe, expect, it } from "vitest";
import {
	castOf,
	formatBytes,
	mediaFacts,
	namesOfType,
	resolutionLabel,
} from "./item-info";

describe("resolutionLabel", () => {
	it("judges by width as well as height", () => {
		expect(resolutionLabel(3840, 2160)).toBe("4K");
		expect(resolutionLabel(1920, 800)).toBe("1080p");
		expect(resolutionLabel(1280, 720)).toBe("720p");
		expect(resolutionLabel(720, 480)).toBe("480p");
		expect(resolutionLabel(undefined, undefined)).toBeUndefined();
	});
});

describe("formatBytes", () => {
	it("uses GB for big files and MB for small ones", () => {
		expect(formatBytes(2.5 * 1024 ** 3)).toBe("2.5 GB");
		expect(formatBytes(300 * 1024 ** 2)).toBe("300 MB");
	});
});

describe("credits", () => {
	const item: BaseItemDto = {
		People: [
			{ Id: "1", Name: "Ann", Type: "Actor", Role: "Lead" },
			{ Id: "2", Name: "Bo", Type: "Director" },
			{ Id: "3", Name: "Bo", Type: "Director" },
			{ Id: "4", Name: "Cy", Type: "GuestStar" },
			{ Name: "No id", Type: "Actor" },
		],
	};

	it("lists unique names for a role", () => {
		expect(namesOfType(item, "Director")).toEqual(["Bo"]);
	});

	it("builds the cast from actors and guest stars with an id", () => {
		expect(castOf(item).map((person) => person.Name)).toEqual(["Ann", "Cy"]);
	});
});

describe("mediaFacts", () => {
	it("summarises the first source's video, audio and size", () => {
		const item: BaseItemDto = {
			MediaSources: [
				{
					Size: 4 * 1024 ** 3,
					MediaStreams: [
						{
							Type: "Video",
							Width: 1920,
							Height: 800,
							Codec: "hevc",
							VideoRangeType: "HDR10",
						},
						{ Type: "Audio", DisplayTitle: "English - EAC3 - 5.1" },
					],
				},
			],
		};
		expect(mediaFacts(item)).toEqual([
			{ label: "Video", value: "1080p · HEVC · HDR10" },
			{ label: "Audio", value: "English - EAC3 - 5.1" },
			{ label: "Size", value: "4.0 GB" },
		]);
	});

	it("returns nothing when there is no media info", () => {
		expect(mediaFacts({})).toEqual([]);
	});
});
