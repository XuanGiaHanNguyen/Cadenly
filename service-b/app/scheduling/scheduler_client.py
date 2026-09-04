"""
Client for Service A's /api/tasks/submit endpoint.

Error handling policy (see Phase 7 design notes):
- Service A unreachable/timed out: one retry after a short backoff for a
  genuine transient blip, then fail loud (raise) - no silent drop, no
  indefinite retry/queueing. That's explicitly the seam for a future
  message queue with real retry/backoff, not something to hand-roll further
  here.
- Service A rejects the request outright (4xx/5xx): that means Service B
  sent something malformed - a bug, not a business outcome. Fail loud
  immediately, no retry.
- A 200 OK response with entries in `rejected`/`unresolved` is NOT an
  error - it's Service A correctly reporting a legitimate business
  outcome. This client does not special-case that; it just returns it.
"""

import os
import time

import httpx

_DEFAULT_BASE_URL = "http://localhost:8080"
_REQUEST_TIMEOUT_SECONDS = 10.0
_RETRY_BACKOFF_SECONDS = 1.0


class SchedulerClientError(Exception):
    """Raised when Service A is unreachable after retrying, or rejects the
    request outright (4xx/5xx) - a transport/contract failure, not a
    business outcome."""


class SchedulerClient:
    def __init__(self, transport: httpx.BaseTransport | None = None) -> None:
        self._base_url = os.environ.get("SERVICE_A_BASE_URL", _DEFAULT_BASE_URL)
        self._transport = transport  # injectable for tests (httpx.MockTransport); None = real network

    def submit_tasks(self, tasks: list[dict]) -> dict:
        payload = {"tasks": tasks}
        last_error: Exception | None = None

        for attempt in range(2):  # one real attempt + one retry
            try:
                with httpx.Client(timeout=_REQUEST_TIMEOUT_SECONDS, transport=self._transport) as client:
                    response = client.post(f"{self._base_url}/api/tasks/submit", json=payload)
                response.raise_for_status()
                return response.json()
            except httpx.HTTPStatusError as e:
                # Service A actively rejected the request - a contract bug, not
                # a transient issue. Fail loud immediately, no retry.
                raise SchedulerClientError(
                    f"Service A rejected the task submission: {e.response.status_code} {e.response.text}"
                ) from e
            except httpx.TransportError as e:
                last_error = e
                if attempt == 0:
                    time.sleep(_RETRY_BACKOFF_SECONDS)
                    continue

        raise SchedulerClientError(f"Service A unreachable after retry: {last_error}") from last_error
