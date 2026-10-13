import type { ApiErrorBody, AuthResponse } from "@/types/api";

/**
 * Central HTTP client. The access token lives only in memory; the refresh token is an HttpOnly
 * cookie scoped to /api/v1/auth that the browser sends automatically. A 401 triggers exactly one
 * shared refresh attempt and a single retry.
 */

export const API_BASE_URL = (process.env.NEXT_PUBLIC_API_BASE_URL || "/api/v1").replace(/\/$/, "");

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: Record<string, string>;
  readonly traceId?: string;

  constructor(status: number, code: string, message: string, fieldErrors: Record<string, string> = {}, traceId?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.traceId = traceId;
  }
}

let accessToken: string | null = null;
let accessExpiresAt = 0;
let refreshInFlight: Promise<AuthResponse | null> | null = null;
const sessionListeners = new Set<(session: AuthResponse | null) => void>();

export function onSessionChange(listener: (session: AuthResponse | null) => void): () => void {
  sessionListeners.add(listener);
  return () => sessionListeners.delete(listener);
}

export function setSession(session: AuthResponse | null) {
  accessToken = session?.accessToken ?? null;
  accessExpiresAt = session ? new Date(session.accessTokenExpiresAt).getTime() : 0;
  sessionListeners.forEach((l) => l(session));
}

export function hasAccessToken() {
  return accessToken !== null;
}

/** Exchanges the refresh cookie for a new access token. Concurrent callers share one request. */
export function refreshSession(): Promise<AuthResponse | null> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const res = await fetch(`${API_BASE_URL}/auth/refresh`, {
          method: "POST",
          credentials: "include",
          headers: { Accept: "application/json" },
        });
        if (!res.ok) {
          setSession(null);
          return null;
        }
        const session = (await res.json()) as AuthResponse;
        setSession(session);
        return session;
      } catch {
        return null;
      } finally {
        refreshInFlight = null;
      }
    })();
  }
  return refreshInFlight;
}

type Query = Record<string, string | number | boolean | null | undefined>;

export interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  form?: FormData;
  query?: Query;
  signal?: AbortSignal;
}

function buildUrl(path: string, query?: Query) {
  const params = new URLSearchParams();
  Object.entries(query ?? {}).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== "") params.set(k, String(v));
  });
  const qs = params.toString();
  return `${API_BASE_URL}${path}${qs ? `?${qs}` : ""}`;
}

async function toError(res: Response): Promise<ApiError> {
  try {
    const body = (await res.json()) as Partial<ApiErrorBody>;
    return new ApiError(res.status, body.code ?? "ERROR", body.message ?? res.statusText, body.fieldErrors ?? {}, body.traceId);
  } catch {
    return new ApiError(res.status, "ERROR", res.status === 413 ? "The upload is too large." : "The server returned an unexpected response.");
  }
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}, retried = false): Promise<T> {
  // Refresh shortly before expiry so long-lived tabs keep working.
  if (accessToken && accessExpiresAt - Date.now() < 30_000) {
    await refreshSession();
  }
  const headers: Record<string, string> = { Accept: "application/json" };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  let body: BodyInit | undefined;
  if (options.form) {
    body = options.form;
  } else if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(options.body);
  }

  let res: Response;
  try {
    res = await fetch(buildUrl(path, options.query), {
      method: options.method ?? "GET",
      headers,
      body,
      credentials: "include",
      signal: options.signal,
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") throw e;
    throw new ApiError(0, "NETWORK_ERROR", "Cannot reach the Bachelor Chef server. Check your connection and try again.");
  }

  if (res.status === 401 && !retried && !path.startsWith("/auth/")) {
    const session = await refreshSession();
    if (session) return apiRequest<T>(path, options, true);
  }
  if (!res.ok) throw await toError(res);
  if (res.status === 204 || res.headers.get("content-length") === "0") return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** Turns any thrown value into a message suitable for a toast or error state. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return "Something went wrong.";
}

/** Downloads an authenticated file (e.g. an import-issues CSV) and saves it in the browser. */
export async function apiDownload(path: string, filename: string): Promise<void> {
  if (!accessToken || accessExpiresAt - Date.now() < 30_000) await refreshSession();
  const res = await fetch(`${API_BASE_URL}${path}`, {
    credentials: "include",
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
  });
  if (!res.ok) throw await toError(res);
  const url = URL.createObjectURL(await res.blob());
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
