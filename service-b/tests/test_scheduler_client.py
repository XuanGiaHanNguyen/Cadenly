import httpx
import pytest

from app.scheduling.scheduler_client import SchedulerClient, SchedulerClientError


def test_successful_submission_returns_parsed_response():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"placed": [], "rejected": [], "unresolved": []})

    client = SchedulerClient(transport=httpx.MockTransport(handler))
    result = client.submit_tasks([{"ownerName": "John", "description": "x", "deadline": "2026-09-04T00:00:00Z", "priority": 5, "estimatedDurationMinutes": 30}])

    assert result == {"placed": [], "rejected": [], "unresolved": []}


def test_4xx_response_fails_loud_immediately_no_retry():
    call_count = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal call_count
        call_count += 1
        return httpx.Response(400, text="malformed request")

    client = SchedulerClient(transport=httpx.MockTransport(handler))

    with pytest.raises(SchedulerClientError, match="rejected"):
        client.submit_tasks([])

    assert call_count == 1  # no retry on a contract-level rejection


def test_transport_error_retries_once_then_fails_loud():
    call_count = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal call_count
        call_count += 1
        raise httpx.ConnectError("connection refused", request=request)

    client = SchedulerClient(transport=httpx.MockTransport(handler))

    with pytest.raises(SchedulerClientError, match="unreachable after retry"):
        client.submit_tasks([])

    assert call_count == 2  # one attempt + one retry


def test_transport_error_then_success_on_retry_returns_result():
    call_count = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal call_count
        call_count += 1
        if call_count == 1:
            raise httpx.ConnectError("connection refused", request=request)
        return httpx.Response(200, json={"placed": [], "rejected": [], "unresolved": []})

    client = SchedulerClient(transport=httpx.MockTransport(handler))
    result = client.submit_tasks([])

    assert result == {"placed": [], "rejected": [], "unresolved": []}
    assert call_count == 2
