import { RequireAuth } from "../auth/RequireAuth";
import { AppShell } from "../components/AppShell";

/** Everything behind login: signed-out visitors are redirected, signed-in ones get the nav shell. */
export function AppLayout() {
	return (
		<RequireAuth>
			<AppShell />
		</RequireAuth>
	);
}
