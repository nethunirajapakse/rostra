import { toast } from "../store/toasts";
import { useSession } from "../store/session";

export class ApiError extends Error {
  status: number;
  messages: string[];

  constructor(status: number, messages: string[]) {
    super(messages[0] ?? "Something went wrong");
    this.status = status;
    this.messages = messages;
  }
}

/** The gateway sets a readable XSRF-TOKEN cookie; unsafe requests must echo it in X-XSRF-TOKEN. */
function xsrfToken(): string | undefined {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : undefined;
}

/** The services answer errors in a few shapes: {messages[]}, {message, fieldErrors}, or plain text. */
async function readErrorMessages(res: Response): Promise<string[]> {
  try {
    const body = await res.json();
    if (Array.isArray(body?.messages) && body.messages.length) return body.messages.map(String);
    const out: string[] = [];
    if (body?.fieldErrors && typeof body.fieldErrors === "object") out.push(...Object.values<string>(body.fieldErrors));
    if (!out.length && typeof body?.message === "string" && body.message) out.push(body.message);
    if (!out.length && typeof body?.error === "string") out.push(body.error);
    if (out.length) return out;
  } catch {
    /* not JSON */
  }
  if (res.status === 401) return ["Sign in to continue"];
  if (res.status === 403) return ["You are not allowed to do that"];
  if (res.status === 404) return ["Not found"];
  if (res.status >= 500) return ["The server had a problem. Try again in a moment."];
  return ["Request failed"];
}

let refreshing: Promise<boolean> | null = null;

/** Trade the refresh cookie for a new access cookie. Concurrent callers share one request. */
export function refreshSession(): Promise<boolean> {
  refreshing ??= fetch("/auth/refresh", {
    method: "POST",
    headers: { "X-XSRF-TOKEN": xsrfToken() ?? "" },
    credentials: "same-origin",
  })
    .then((res) => res.ok)
    .catch(() => false)
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

const NO_REFRESH = new Set(["/auth/signin", "/auth/signup", "/auth/refresh"]);

async function request<T>(method: string, path: string, body?: unknown, canRefresh = true): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  // A FormData body (file upload) sets its own multipart Content-Type, boundary included.
  const isForm = body instanceof FormData;
  if (body !== undefined && !isForm) headers["Content-Type"] = "application/json";
  if (method !== "GET") {
    const token = xsrfToken();
    if (token) headers["X-XSRF-TOKEN"] = token;
  }

  const res = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
    credentials: "same-origin",
  });

  if (res.status === 401 && canRefresh && !NO_REFRESH.has(path.split("?")[0])) {
    if (await refreshSession()) return request<T>(method, path, body, false);
    if (useSession.getState().status === "authed") {
      useSession.getState().clear();
      toast.warn("Your session expired. Sign in again to continue.");
    }
  }

  if (!res.ok) throw new ApiError(res.status, await readErrorMessages(res));
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body ?? {}),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, body ?? {}),
  patch: <T>(path: string, body?: unknown) => request<T>("PATCH", path, body ?? {}),
  upload: <T>(path: string, form: FormData) => request<T>("POST", path, form),
  del: <T>(path: string) => request<T>("DELETE", path),
};

export const errorText = (e: unknown): string =>
  e instanceof ApiError ? e.messages.join(". ") : "Could not reach the server. Check your connection.";
