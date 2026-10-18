import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider } from "@tanstack/react-router";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { AuthProvider } from "./auth/AuthProvider";
import { MotionRoot } from "./components/MotionRoot";
import { initSettings } from "./lib/settings";
import { router } from "./router";
import "./styles.css";

const queryClient = new QueryClient({
	defaultOptions: {
		queries: {
			staleTime: 30_000,
			refetchOnWindowFocus: false,
			retry: 1,
		},
	},
});

// Before the first render, so the page never flashes the default look.
initSettings();

const container = document.getElementById("root");
if (!container) throw new Error("Root element #root not found");

createRoot(container).render(
	<StrictMode>
		<QueryClientProvider client={queryClient}>
			<MotionRoot>
				<AuthProvider>
					<RouterProvider router={router} />
				</AuthProvider>
			</MotionRoot>
		</QueryClientProvider>
	</StrictMode>,
);
