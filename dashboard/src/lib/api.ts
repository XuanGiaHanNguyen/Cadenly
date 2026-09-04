export const BACKEND_URL = "http://localhost:8080";

export type CurrentUser = {
  id: string;
  email: string;
  displayName: string;
  occupation: string | null;
  calendarPreference: string | null;
};

function getCookie(name: string): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

const MUTATING_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

/**
 * Every request to scheduler-engine should go through this, not a bare
 * fetch: it always sends the session cookie (credentials: "include") and,
 * for state-changing methods, mirrors the XSRF-TOKEN cookie into the
 * X-XSRF-TOKEN header Spring Security expects. Harmless to include on
 * CSRF-exempt endpoints like /api/tasks/submit - the server just ignores
 * it there.
 */
export async function apiFetch(path: string, options: RequestInit = {}): Promise<Response> {
  const method = (options.method ?? "GET").toUpperCase();
  const headers = new Headers(options.headers);

  if (MUTATING_METHODS.has(method)) {
    const csrfToken = getCookie("XSRF-TOKEN");
    if (csrfToken) {
      headers.set("X-XSRF-TOKEN", csrfToken);
    }
  }

  return fetch(`${BACKEND_URL}${path}`, {
    ...options,
    credentials: "include",
    headers,
  });
}

/** Null if not logged in (401) rather than throwing - callers decide what to do about it. */
export async function fetchCurrentUser(): Promise<CurrentUser | null> {
  const response = await apiFetch("/api/auth/me");
  if (!response.ok) return null;
  return response.json();
}

export function hasCompletedOnboarding(user: CurrentUser): boolean {
  return user.occupation != null && user.occupation.length > 0;
}
