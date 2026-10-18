import { getAuthenticationApi } from "@jellyfin/sdk/lib/utils/api/authentication-api";
import { getSessionApi } from "@jellyfin/sdk/lib/utils/api/session-api";
import { getUserApi } from "@jellyfin/sdk/lib/utils/api/user-api";
import { useQueryClient } from "@tanstack/react-query";
import {
	createContext,
	type ReactNode,
	useCallback,
	useContext,
	useEffect,
	useMemo,
	useState,
} from "react";
import { getHttpStatus } from "../lib/errors";
import { api } from "../lib/jellyfin";
import {
	clearSession,
	loadSession,
	type Session,
	saveSession,
} from "./session";

type AuthState =
	| { status: "loading" }
	| { status: "signedOut" }
	| { status: "signedIn"; session: Session };

type AuthContextValue = {
	state: AuthState;
	/** Rejects with the underlying error when sign-in fails (check it with getHttpStatus). */
	signIn: (username: string, password: string) => Promise<void>;
	signOut: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
	const queryClient = useQueryClient();
	const [state, setState] = useState<AuthState>({ status: "loading" });

	// On startup: restore a saved session and check the server still accepts its token.
	useEffect(() => {
		const controller = new AbortController();
		const saved = loadSession();

		if (!saved) {
			setState({ status: "signedOut" });
			return;
		}

		api.update({ accessToken: saved.accessToken });
		getUserApi(api)
			.getCurrentUser({ signal: controller.signal })
			.then((response) => {
				const user = response.data;
				setState({
					status: "signedIn",
					session: {
						...saved,
						userName: user.Name ?? saved.userName,
						primaryImageTag: user.PrimaryImageTag ?? undefined,
					},
				});
			})
			.catch((error: unknown) => {
				if (controller.signal.aborted) return;
				// Only throw the session away when the server says the token is bad.
				// If the server is just unreachable, keep it so a reload works once it's back.
				const status = getHttpStatus(error);
				if (status === 401 || status === 403) clearSession();
				api.update({ accessToken: "" });
				setState({ status: "signedOut" });
			});

		return () => controller.abort();
	}, []);

	// If the server rejects our token mid-session (expired, revoked from the admin UI), sign out locally.
	useEffect(() => {
		const id = api.axiosInstance.interceptors.response.use(
			undefined,
			(error: unknown) => {
				if (getHttpStatus(error) === 401 && api.accessToken) {
					api.update({ accessToken: "" });
					clearSession();
					queryClient.clear();
					setState({ status: "signedOut" });
				}
				return Promise.reject(error);
			},
		);
		return () => api.axiosInstance.interceptors.response.eject(id);
	}, [queryClient]);

	const signIn = useCallback(
		async (username: string, password: string) => {
			const response = await getAuthenticationApi(api).authenticateUserByName({
				authenticateUserByName: { Username: username, Pw: password },
			});
			const { AccessToken, User } = response.data;
			if (!AccessToken || !User?.Id) {
				throw new Error("The server returned an unexpected response.");
			}

			api.update({ accessToken: AccessToken });
			const session: Session = {
				userId: User.Id,
				userName: User.Name ?? username,
				accessToken: AccessToken,
				primaryImageTag: User.PrimaryImageTag ?? undefined,
			};
			saveSession(session);
			queryClient.clear();
			setState({ status: "signedIn", session });
		},
		[queryClient],
	);

	const signOut = useCallback(async () => {
		try {
			await getSessionApi(api).reportSessionEnded();
		} catch {
			// Server unreachable or the token was already invalid. Sign out locally regardless.
		}
		api.update({ accessToken: "" });
		clearSession();
		queryClient.clear();
		setState({ status: "signedOut" });
	}, [queryClient]);

	const value = useMemo(
		() => ({ state, signIn, signOut }),
		[state, signIn, signOut],
	);

	return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
	const context = useContext(AuthContext);
	if (!context) throw new Error("useAuth must be used inside <AuthProvider>");
	return context;
}

/** The signed-in user's session. Only call this from screens behind <RequireAuth>. */
export function useSession(): Session {
	const { state } = useAuth();
	if (state.status !== "signedIn") {
		throw new Error("useSession requires a signed-in user");
	}
	return state.session;
}
