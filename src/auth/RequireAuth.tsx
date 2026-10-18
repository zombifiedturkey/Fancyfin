import { Navigate } from "@tanstack/react-router";
import type { ReactNode } from "react";
import { Splash } from "../components/Splash";
import { useAuth } from "./AuthProvider";

/** Shows the splash while the saved session is checked, and sends signed-out users to /login. */
export function RequireAuth({ children }: { children: ReactNode }) {
	const { state } = useAuth();

	if (state.status === "loading") return <Splash />;
	if (state.status === "signedOut") return <Navigate to="/login" replace />;
	return <>{children}</>;
}
