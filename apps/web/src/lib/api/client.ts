type ApiErrorBody = {
  code?: unknown;
  detail?: unknown;
  error?: unknown;
  message?: unknown;
};

type RequestOptions = Omit<RequestInit, "body"> & {
  body?: BodyInit | Record<string, unknown> | null;
  errorMessage?: string;
};

export class NexusApiError extends Error {
  status: number;
  code?: string;
  data: unknown;

  constructor(
    message: string,
    options: { status: number; code?: string; data?: unknown },
  ) {
    super(message);
    this.name = "NexusApiError";
    this.status = options.status;
    this.code = options.code;
    this.data = options.data;
  }
}

function isBodyInit(body: RequestOptions["body"]): body is BodyInit {
  return (
    typeof body === "string" ||
    body instanceof Blob ||
    body instanceof FormData ||
    body instanceof URLSearchParams ||
    body instanceof ArrayBuffer
  );
}

async function parseResponseBody(response: Response) {
  if (response.status === 204) {
    return null;
  }

  const contentType = response.headers.get("content-type") ?? "";
  if (contentType.includes("json")) {
    return response.json().catch(() => null);
  }

  return response.text().catch(() => "");
}

function errorDetails(body: unknown, fallback: string) {
  if (!body || typeof body !== "object") {
    return { message: fallback };
  }

  const problem = body as ApiErrorBody;
  const message = [problem.detail, problem.message, problem.error].find(
    (value): value is string => typeof value === "string" && value.trim() !== "",
  );
  const code = typeof problem.code === "string" ? problem.code : undefined;
  return { message: message ?? fallback, code };
}

export async function apiRequest<T>(
  url: string,
  options: RequestOptions = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");

  let body = options.body;
  if (body != null && !isBodyInit(body)) {
    headers.set("Content-Type", "application/json");
    body = JSON.stringify(body);
  }

  const response = await fetch(url, {
    ...options,
    body: body as BodyInit | null | undefined,
    headers,
    credentials: options.credentials ?? "include",
    cache: options.cache ?? "no-store",
  });
  const responseBody = await parseResponseBody(response);

  if (!response.ok) {
    const fallback = options.errorMessage ?? "No se pudo completar la operación.";
    const { message, code } = errorDetails(responseBody, fallback);

    // Interceptor global: una cuenta suspendida o desactivada redirige a la
    // página dedicada, independientemente del endpoint que reciba el 403.
    if (
      response.status === 403 &&
      code === "account_suspended" &&
      typeof window !== "undefined"
    ) {
      window.location.assign("/login/suspended");
    }

    throw new NexusApiError(message, {
      status: response.status,
      code,
      data: responseBody,
    });
  }

  return responseBody as T;
}

export const apiClient = {
  get: <T>(url: string, options: RequestOptions = {}) =>
    apiRequest<T>(url, { ...options, method: "GET" }),
  post: <T>(
    url: string,
    body?: RequestOptions["body"],
    options: RequestOptions = {},
  ) => apiRequest<T>(url, { ...options, method: "POST", body }),
  delete: <T>(url: string, options: RequestOptions = {}) =>
    apiRequest<T>(url, { ...options, method: "DELETE" }),
  patch: <T>(
    url: string,
    body?: RequestOptions["body"],
    options: RequestOptions = {},
  ) => apiRequest<T>(url, { ...options, method: "PATCH", body }),
  put: <T>(
    url: string,
    body?: RequestOptions["body"],
    options: RequestOptions = {},
  ) => apiRequest<T>(url, { ...options, method: "PUT", body }),
};
