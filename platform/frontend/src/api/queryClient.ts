import { QueryClient } from "@tanstack/react-query";
import { ApiError } from "../lib/api";

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5_000,
      // Client errors (401, 403, 404...) will not fix themselves by retrying.
      retry: (failures, error) => !(error instanceof ApiError && error.status < 500) && failures < 2,
    },
  },
});
