import { getAccessToken } from "@/lib/tokenStorage";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "/api";
const MOCKS_MODE = (import.meta.env.VITE_USE_MOCKS ?? "auto").toString().toLowerCase();

type MocksMode = "true" | "false" | "auto";
const normalizedMocksMode: MocksMode = MOCKS_MODE === "true" ? "true" : MOCKS_MODE === "false" ? "false" : "auto";

export const mocksMode = normalizedMocksMode;
export const forceMocks = mocksMode === "true";
export const autoMocks = mocksMode === "auto";

export const shouldUseMocks = () => forceMocks;
export const shouldAutoFallbackToMocks = () => autoMocks;

export class ApiError extends Error {
  status?: number;
  body?: string;

  constructor(message: string, status?: number, body?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
  }
}

const joinUrl = (base: string, path: string) => {
  if (path.startsWith("http://") || path.startsWith("https://")) {
    return path;
  }
  const trimmedBase = base.endsWith("/") ? base.slice(0, -1) : base;
  const trimmedPath = path.startsWith("/") ? path.slice(1) : path;
  return `${trimmedBase}/${trimmedPath}`;
};

const buildHeaders = (headers: HeadersInit | undefined, body?: BodyInit | null) => {
  const next = new Headers(headers || undefined);
  if (!(body instanceof FormData) && body !== undefined && body !== null && !next.has("Content-Type")) {
    next.set("Content-Type", "application/json");
  }
  if (!next.has("Accept")) {
    next.set("Accept", "application/json");
  }
  const token = getAccessToken();
  if (token && !next.has("Authorization")) {
    next.set("Authorization", `Bearer ${token}`);
  }
  return next;
};

const readResponseBody = async (response: Response) => {
  const contentType = response.headers.get("content-type") || "";
  if (response.status === 204) {
    return null;
  }
  if (contentType.includes("application/json")) {
    return response.json();
  }
  return response.text();
};

const apiRequest = async <T>(path: string, options: RequestInit = {}): Promise<T> => {
  const response = await fetch(joinUrl(API_BASE_URL, path), {
    credentials: "include",
    ...options,
    headers: buildHeaders(options.headers, options.body),
  });

  if (!response.ok) {
    const bodyText = await response.text().catch(() => "");
    throw new ApiError(`API request failed: ${response.status}`, response.status, bodyText);
  }

  return readResponseBody(response) as Promise<T>;
};

export const apiGet = <T>(path: string) => apiRequest<T>(path, { method: "GET" });

export const apiPost = <T>(path: string, body?: unknown) =>
  apiRequest<T>(path, {
    method: "POST",
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

export const apiPut = <T>(path: string, body?: unknown) =>
  apiRequest<T>(path, {
    method: "PUT",
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

export const apiDelete = <T>(path: string) => apiRequest<T>(path, { method: "DELETE" });

export const apiUpload = <T>(path: string, formData: FormData) =>
  apiRequest<T>(path, {
    method: "POST",
    body: formData,
  });
