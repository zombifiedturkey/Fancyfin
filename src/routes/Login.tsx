import { getUserApi } from "@jellyfin/sdk/lib/utils/api/user-api";
import { useQuery } from "@tanstack/react-query";
import { Navigate } from "@tanstack/react-router";
import { AnimatePresence, type MotionProps, motion } from "motion/react";
import { type FormEvent, useState } from "react";
import { useAuth } from "../auth/AuthProvider";
import { Aurora } from "../components/Aurora";
import { ConnectServer } from "../components/ConnectServer";
import { ServerStatus } from "../components/ServerStatus";
import { UserAvatar } from "../components/UserAvatar";
import { getServerUrl } from "../lib/api-base";
import { getHttpStatus } from "../lib/errors";
import { api } from "../lib/jellyfin";
import { loadRuntimeConfig } from "../lib/runtime-config";

type Profile = {
	id: string;
	name: string;
	imageTag?: string;
	hasPassword: boolean;
};

type Choice = { kind: "profile"; profile: Profile } | { kind: "manual" };

/** The accounts the server is willing to show on its login screen. */
async function fetchProfiles(signal?: AbortSignal): Promise<Profile[]> {
	const response = await getUserApi(api).getPublicUsers({ signal });
	return response.data.flatMap((user) =>
		user.Id && user.Name
			? [
					{
						id: user.Id,
						name: user.Name,
						imageTag: user.PrimaryImageTag ?? undefined,
						hasPassword: user.HasPassword !== false,
					},
				]
			: [],
	);
}

function signInErrorMessage(error: unknown): string {
	const status = getHttpStatus(error);
	if (status === 401) return "Incorrect username or password.";
	if (status === 403) return "This account can't sign in right now.";
	if (status === undefined)
		return "Can't reach the server. Check your connection and try again.";
	return `Sign-in failed (error ${status}).`;
}

const fade: MotionProps = {
	initial: { opacity: 0, y: 12 },
	animate: { opacity: 1, y: 0 },
	exit: { opacity: 0, y: -8 },
	transition: { duration: 0.28, ease: [0.2, 0, 0, 1] },
};

function LoginScreen({ onChangeServer }: { onChangeServer: () => void }) {
	const { state, signIn } = useAuth();
	const [choice, setChoice] = useState<Choice | null>(null);
	const [quickSigningInId, setQuickSigningInId] = useState<string | null>(null);

	const profilesQuery = useQuery({
		queryKey: ["users", "public"],
		queryFn: ({ signal }) => fetchProfiles(signal),
		retry: false,
	});

	if (state.status === "signedIn") return <Navigate to="/" replace />;

	const profiles = profilesQuery.data ?? [];
	// If the server lists no profiles, go straight to typing a username.
	const activeChoice: Choice | null =
		choice ??
		(profilesQuery.isSuccess && profiles.length === 0
			? { kind: "manual" }
			: null);

	async function pickProfile(profile: Profile) {
		if (profile.hasPassword) {
			setChoice({ kind: "profile", profile });
			return;
		}
		// No password set: sign straight in. If that somehow fails, fall back to the form.
		setQuickSigningInId(profile.id);
		try {
			await signIn(profile.name, "");
		} catch {
			setQuickSigningInId(null);
			setChoice({ kind: "profile", profile });
		}
	}

	return (
		<div className="relative isolate flex min-h-dvh flex-col items-center justify-center px-4 py-16">
			<Aurora />

			<div className="mb-10 flex items-center gap-2.5 text-lg font-semibold tracking-tight">
				<span
					aria-hidden
					className="inline-block size-6 rounded-full bg-linear-to-br from-accent-strong to-accent"
				/>
				Fancyfin
			</div>

			<AnimatePresence mode="wait">
				{activeChoice ? (
					<motion.div key="form" className="w-full max-w-sm" {...fade}>
						<SignInForm
							choice={activeChoice}
							onBack={
								profiles.length > 0 || choice?.kind === "profile"
									? () => setChoice(null)
									: undefined
							}
						/>
					</motion.div>
				) : (
					<motion.div
						key="picker"
						className="flex w-full flex-col items-center"
						{...fade}
					>
						<h1 className="mb-10 text-center text-3xl font-semibold tracking-tight">
							Choose a profile
						</h1>

						{profilesQuery.isPending && <ProfileSkeleton />}

						{profilesQuery.isError && (
							<div className="flex flex-col items-center gap-4 text-center">
								<p className="max-w-sm text-sm text-ink-muted">
									Couldn't load profiles from the server. You can still sign in
									with a username.
								</p>
								<OtherAccountButton
									onClick={() => setChoice({ kind: "manual" })}
								/>
							</div>
						)}

						{profilesQuery.isSuccess && (
							<ul className="flex max-w-3xl flex-wrap justify-center gap-x-8 gap-y-10">
								{profiles.map((profile) => (
									<li key={profile.id}>
										<motion.button
											type="button"
											disabled={quickSigningInId !== null}
											onClick={() => void pickProfile(profile)}
											whileHover={{ scale: 1.07 }}
											whileFocus={{ scale: 1.07 }}
											whileTap={{ scale: 0.97 }}
											transition={{
												type: "spring",
												stiffness: 320,
												damping: 24,
											}}
											className="group flex w-28 flex-col items-center gap-3 rounded-2xl p-1"
										>
											<UserAvatar
												userId={profile.id}
												name={profile.name}
												imageTag={profile.imageTag}
												className={`size-28 text-4xl shadow-card ring-1 ring-white/10 transition-shadow group-hover:ring-2 group-hover:ring-white/60 group-focus-visible:ring-2 group-focus-visible:ring-white/60 ${
													quickSigningInId === profile.id ? "animate-pulse" : ""
												}`}
											/>
											<span className="max-w-full truncate text-sm text-ink-muted transition-colors group-hover:text-ink">
												{profile.name}
											</span>
										</motion.button>
									</li>
								))}
								<li>
									<OtherAccountButton
										onClick={() => setChoice({ kind: "manual" })}
									/>
								</li>
							</ul>
						)}
					</motion.div>
				)}
			</AnimatePresence>

			<div className="mt-14 w-full max-w-md">
				<ServerStatus onChangeServer={onChangeServer} />
			</div>
		</div>
	);
}

/**
 * The login page. On a fresh Docker install with no JELLYFIN_URL, or when the user taps "Change server",
 * it asks for the Jellyfin address first; otherwise it shows the profile picker.
 */
export function Login() {
	const config = useQuery({
		queryKey: ["runtime-config"],
		queryFn: ({ signal }) => loadRuntimeConfig(signal),
		staleTime: Number.POSITIVE_INFINITY,
	});
	const [changingServer, setChangingServer] = useState(false);
	const hasChosenServer = getServerUrl() !== null;

	if (!hasChosenServer && config.isPending) {
		return (
			<div className="relative isolate min-h-dvh">
				<Aurora />
			</div>
		);
	}

	const needsServer =
		changingServer || (!hasChosenServer && config.data?.proxy === false);

	if (needsServer) {
		return (
			<div className="relative isolate flex min-h-dvh flex-col items-center justify-center px-4 py-16">
				<Aurora />
				<div className="mb-10 flex items-center gap-2.5 text-lg font-semibold tracking-tight">
					<span
						aria-hidden
						className="inline-block size-6 rounded-full bg-linear-to-br from-accent-strong to-accent"
					/>
					Fancyfin
				</div>
				<ConnectServer
					onCancel={changingServer ? () => setChangingServer(false) : undefined}
				/>
			</div>
		);
	}

	return <LoginScreen onChangeServer={() => setChangingServer(true)} />;
}

function ProfileSkeleton() {
	return (
		<ul className="flex gap-8" aria-label="Loading profiles">
			{[0, 1, 2].map((i) => (
				<li key={i} className="flex w-28 flex-col items-center gap-3 p-1">
					<span className="shimmer size-28 rounded-full" />
					<span className="shimmer h-3 w-16 rounded-pill" />
				</li>
			))}
		</ul>
	);
}

function OtherAccountButton({ onClick }: { onClick: () => void }) {
	return (
		<motion.button
			type="button"
			onClick={onClick}
			whileHover={{ scale: 1.07 }}
			whileFocus={{ scale: 1.07 }}
			whileTap={{ scale: 0.97 }}
			transition={{ type: "spring", stiffness: 320, damping: 24 }}
			className="group flex w-28 flex-col items-center gap-3 rounded-2xl p-1"
		>
			<span className="glass grid size-28 place-items-center rounded-full text-4xl font-light text-ink-muted transition-colors group-hover:text-ink">
				+
			</span>
			<span className="text-sm text-ink-muted transition-colors group-hover:text-ink">
				Other
			</span>
		</motion.button>
	);
}

function SignInForm({
	choice,
	onBack,
}: {
	choice: Choice;
	onBack?: () => void;
}) {
	const { signIn } = useAuth();
	const isManual = choice.kind === "manual";
	const [username, setUsername] = useState(
		choice.kind === "profile" ? choice.profile.name : "",
	);
	const [password, setPassword] = useState("");
	const [error, setError] = useState<string | null>(null);
	const [busy, setBusy] = useState(false);

	async function submit(event: FormEvent<HTMLFormElement>) {
		event.preventDefault();
		if (busy || !username.trim()) return;
		setBusy(true);
		setError(null);
		try {
			await signIn(username.trim(), password);
			// On success the auth state changes and this screen is replaced.
		} catch (caught) {
			setError(signInErrorMessage(caught));
			setBusy(false);
		}
	}

	const inputClass =
		"glass w-full rounded-pill px-5 py-3 text-sm text-ink placeholder:text-ink-faint focus:bg-glass-strong";

	return (
		<form onSubmit={submit} className="flex flex-col items-center gap-4">
			{choice.kind === "profile" ? (
				<>
					<UserAvatar
						userId={choice.profile.id}
						name={choice.profile.name}
						imageTag={choice.profile.imageTag}
						className="size-24 text-3xl shadow-card ring-1 ring-white/10"
					/>
					<h1 className="mb-2 text-2xl font-semibold tracking-tight">
						{choice.profile.name}
					</h1>
					{/* Lets password managers know which account this password is for */}
					<input
						type="text"
						name="username"
						autoComplete="username"
						value={username}
						readOnly
						tabIndex={-1}
						aria-hidden
						className="sr-only"
					/>
				</>
			) : (
				<h1 className="mb-4 text-2xl font-semibold tracking-tight">Sign in</h1>
			)}

			{isManual && (
				<div className="w-full">
					<label htmlFor="username" className="sr-only">
						Username
					</label>
					<input
						id="username"
						name="username"
						type="text"
						autoComplete="username"
						autoCapitalize="none"
						autoCorrect="off"
						spellCheck={false}
						placeholder="Username"
						value={username}
						onChange={(event) => setUsername(event.target.value)}
						// biome-ignore lint/a11y/noAutofocus: this is the only field on the screen
						autoFocus
						className={inputClass}
					/>
				</div>
			)}

			<div className="w-full">
				<label htmlFor="password" className="sr-only">
					Password
				</label>
				<input
					id="password"
					name="password"
					type="password"
					autoComplete="current-password"
					placeholder="Password"
					value={password}
					onChange={(event) => setPassword(event.target.value)}
					// biome-ignore lint/a11y/noAutofocus: the password is the next thing the user types
					autoFocus={!isManual}
					className={inputClass}
				/>
			</div>

			{error && (
				<p role="alert" className="text-center text-sm text-danger">
					{error}
				</p>
			)}

			<button
				type="submit"
				disabled={busy || !username.trim()}
				className="mt-1 w-full rounded-pill bg-ink px-6 py-3 text-sm font-semibold text-canvas transition-opacity hover:opacity-90 disabled:opacity-50"
			>
				{busy ? "Signing in…" : "Sign in"}
			</button>

			{onBack && (
				<button
					type="button"
					onClick={onBack}
					className="rounded-pill px-4 py-2 text-sm text-ink-muted transition-colors hover:text-ink"
				>
					Back
				</button>
			)}
		</form>
	);
}
