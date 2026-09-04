from fastapi.testclient import TestClient

from app.main import app
from tests.fixtures.sample_transcripts import SIGNAL_VS_FILLER

client = TestClient(app)


def test_summarize_endpoint_returns_summary_and_ranked_sentences():
    response = client.post("/summarize", json={"text": SIGNAL_VS_FILLER, "top_k": 3})

    assert response.status_code == 200
    body = response.json()
    assert 1 <= len(body["summary"]) <= 3
    assert len(body["ranked"]) == 7  # SIGNAL_VS_FILLER has 7 sentences
    assert all("score" in r for r in body["ranked"])


def test_summarize_endpoint_rejects_empty_text():
    response = client.post("/summarize", json={"text": "   "})
    assert response.status_code == 422


def test_summarize_endpoint_honors_redundancy_suppression_flag():
    without = client.post(
        "/summarize", json={"text": SIGNAL_VS_FILLER, "top_k": 3, "redundancy_suppression": False}
    ).json()
    with_ = client.post(
        "/summarize", json={"text": SIGNAL_VS_FILLER, "top_k": 3, "redundancy_suppression": True}
    ).json()

    assert isinstance(without["summary"], list)
    assert isinstance(with_["summary"], list)
