import type { DeviceProfile } from "@jellyfin/sdk/lib/generated-client/models/device-profile";

/** True when the browser says it can probably play this MIME type. */
function supports(video: HTMLVideoElement, type: string): boolean {
	return video.canPlayType(type) !== "";
}

/**
 * Describe what this browser can play, so the server can choose direct play
 * when possible and transcode only when it has to.
 */
export function buildDeviceProfile(maxBitrate = 120_000_000): DeviceProfile {
	const video = document.createElement("video");

	const videoCodecs: string[] = [];
	if (supports(video, 'video/mp4; codecs="avc1.640029"'))
		videoCodecs.push("h264");
	if (supports(video, 'video/mp4; codecs="hvc1.1.6.L120.B0"'))
		videoCodecs.push("hevc");
	if (supports(video, 'video/webm; codecs="vp9"')) videoCodecs.push("vp9");
	if (supports(video, 'video/mp4; codecs="av01.0.08M.08"'))
		videoCodecs.push("av1");

	const audioCodecs: string[] = [];
	if (supports(video, 'audio/mp4; codecs="mp4a.40.2"')) audioCodecs.push("aac");
	if (supports(video, "audio/mpeg")) audioCodecs.push("mp3");
	if (supports(video, 'audio/webm; codecs="opus"')) audioCodecs.push("opus");
	if (supports(video, "audio/flac")) audioCodecs.push("flac");
	if (supports(video, 'audio/mp4; codecs="ac-3"')) audioCodecs.push("ac3");
	if (supports(video, 'audio/mp4; codecs="ec-3"')) audioCodecs.push("eac3");

	const webm = supports(video, "video/webm");
	const mkv = supports(video, "video/x-matroska");

	const directPlay: NonNullable<DeviceProfile["DirectPlayProfiles"]> = [
		{
			Container: "mp4,m4v",
			Type: "Video",
			VideoCodec: videoCodecs.filter((c) => c !== "vp9").join(","),
			AudioCodec: audioCodecs.filter((c) => c !== "opus").join(","),
		},
	];
	if (webm) {
		directPlay.push({
			Container: "webm",
			Type: "Video",
			VideoCodec:
				videoCodecs.filter((c) => c === "vp9" || c === "av1").join(",") ||
				"vp9",
			AudioCodec: "opus,vorbis",
		});
	}
	if (mkv) {
		directPlay.push({
			Container: "mkv",
			Type: "Video",
			VideoCodec: videoCodecs.join(","),
			AudioCodec: audioCodecs.join(","),
		});
	}

	return {
		Name: "Fancyfin Web",
		MaxStreamingBitrate: maxBitrate,
		MaxStaticBitrate: maxBitrate,
		DirectPlayProfiles: directPlay,
		TranscodingProfiles: [
			{
				Container: "ts",
				Type: "Video",
				VideoCodec: "h264",
				AudioCodec: "aac,mp3",
				Protocol: "hls",
				Context: "Streaming",
				MaxAudioChannels: "2",
				MinSegments: 1,
				BreakOnNonKeyFrames: true,
			},
		],
		CodecProfiles: [
			{
				Type: "Video",
				Codec: "h264",
				Conditions: [
					{
						Condition: "NotEquals",
						Property: "IsAnamorphic",
						Value: "true",
						IsRequired: false,
					},
					{
						Condition: "LessThanEqual",
						Property: "VideoLevel",
						Value: "52",
						IsRequired: false,
					},
				],
			},
		],
		// Text subtitles are fetched separately and drawn by the player; picture formats get burned in.
		SubtitleProfiles: [
			{ Format: "vtt", Method: "External" },
			{ Format: "ass", Method: "External" },
			{ Format: "ssa", Method: "External" },
		],
	};
}
