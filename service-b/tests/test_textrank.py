import numpy as np
import pytest

from app.summarization.textrank import (
    compute_tfidf_vectors,
    cosine_similarity_matrix,
    pagerank,
    split_sentences,
    summarize,
)
from tests.fixtures.sample_transcripts import (
    FILLER_SENTENCE_PREFIXES,
    REDUNDANCY,
    SIGNAL_SENTENCE_PREFIXES,
    SIGNAL_VS_FILLER,
)


# ---------------------------------------------------------------------------
# Algorithm unit tests: hand-computable, no transcripts involved.
# ---------------------------------------------------------------------------

def test_tfidf_matches_hand_computed_values():
    # "cat dog" / "dog bird": vocabulary = [bird, cat, dog], n=2.
    # idf(term) = ln(n / (1 + df)). df(bird)=1, df(cat)=1, df(dog)=2.
    # idf(bird) = ln(2/2) = 0, idf(cat) = ln(2/2) = 0, idf(dog) = ln(2/3) = -0.405465
    # tf in each 2-token sentence is 0.5 per token.
    tokenized = [["cat", "dog"], ["dog", "bird"]]
    vectors = compute_tfidf_vectors(tokenized)

    # columns are sorted alphabetically: [bird, cat, dog]
    assert vectors.shape == (2, 3)
    bird_col, cat_col, dog_col = 0, 1, 2
    assert vectors[0, cat_col] == pytest.approx(0.0)
    assert vectors[0, dog_col] == pytest.approx(0.5 * np.log(2 / 3))
    assert vectors[0, bird_col] == pytest.approx(0.0)
    assert vectors[1, bird_col] == pytest.approx(0.0)  # idf(bird)=0 -> tfidf 0 even though present
    assert vectors[1, dog_col] == pytest.approx(0.5 * np.log(2 / 3))
    assert vectors[1, cat_col] == pytest.approx(0.0)


def test_cosine_similarity_identical_vectors_is_one():
    vectors = np.array([[1.0, 2.0, 3.0], [1.0, 2.0, 3.0]])
    sim = cosine_similarity_matrix(vectors)
    assert sim[0, 1] == pytest.approx(1.0)
    assert sim[1, 0] == pytest.approx(1.0)


def test_cosine_similarity_disjoint_vectors_is_zero():
    vectors = np.array([[1.0, 0.0], [0.0, 1.0]])
    sim = cosine_similarity_matrix(vectors)
    assert sim[0, 1] == pytest.approx(0.0)


def test_cosine_similarity_zero_vector_does_not_divide_by_zero():
    vectors = np.array([[0.0, 0.0], [1.0, 1.0]])
    sim = cosine_similarity_matrix(vectors)
    assert sim[0, 1] == pytest.approx(0.0)
    assert np.isfinite(sim).all()


def test_pagerank_matches_hand_computed_fixed_point():
    # Path graph A-B-C: w(A,B)=0.5, w(B,C)=0.5, w(A,C)=0, damping=0.85.
    # Hand-solved fixed point (see Phase 5 design writeup):
    #   WS(A) = WS(C) = 57/74 ~= 0.770270
    #   WS(B) = 54/37 ~= 1.459459
    # Tolerance 1e-3: convergence stops when summed delta < 1e-4; the
    # fixed-point-iteration error bound ||x_n - x*|| <~ delta_n * d/(1-d)
    # ~= 1e-4 * 5.67 ~= 5.7e-4, safely under 1e-3.
    weights = np.array(
        [
            [0.0, 0.5, 0.0],
            [0.5, 0.0, 0.5],
            [0.0, 0.5, 0.0],
        ]
    )
    scores = pagerank(weights, damping=0.85, max_iterations=100, convergence_threshold=1e-4)

    assert scores[0] == pytest.approx(57 / 74, abs=1e-3)
    assert scores[1] == pytest.approx(54 / 37, abs=1e-3)
    assert scores[2] == pytest.approx(57 / 74, abs=1e-3)


def test_pagerank_converges_before_max_iterations():
    weights = np.array(
        [
            [0.0, 0.5, 0.0],
            [0.5, 0.0, 0.5],
            [0.0, 0.5, 0.0],
        ]
    )
    n = weights.shape[0]
    scores = np.full(n, 1.0 / n)
    out_sums = weights.sum(axis=1)
    transition = weights / out_sums[:, None]

    iterations_to_converge = None
    for i in range(100):
        new_scores = 0.15 + 0.85 * (transition.T @ scores)
        delta = float(np.sum(np.abs(new_scores - scores)))
        scores = new_scores
        if delta < 1e-4:
            iterations_to_converge = i + 1
            break

    assert iterations_to_converge is not None
    assert iterations_to_converge < 100


def test_pagerank_single_node_has_no_neighbors():
    scores = pagerank(np.array([[0.0]]))
    assert scores[0] == pytest.approx(0.15)


# ---------------------------------------------------------------------------
# Summary-quality tests: semantic assertions, not smoke tests.
# ---------------------------------------------------------------------------

def _starts_with_any(text: str, prefixes: tuple[str, ...]) -> bool:
    return any(text.startswith(p) for p in prefixes)


def test_summary_prefers_signal_over_filler():
    result = summarize(SIGNAL_VS_FILLER, top_k=3)

    for sentence in result.sentences:
        assert not _starts_with_any(sentence, FILLER_SENTENCE_PREFIXES), (
            f"filler sentence leaked into summary: {sentence!r}"
        )
    assert any(_starts_with_any(s, SIGNAL_SENTENCE_PREFIXES) for s in result.sentences)


def test_redundancy_suppression_toggle_changes_the_summary():
    # Same transcript, same top_k - only the flag differs. This is the
    # before/after comparison requested for the design decision.
    without_suppression = summarize(REDUNDANCY, top_k=2, redundancy_suppression=False)
    with_suppression = summarize(REDUNDANCY, top_k=2, redundancy_suppression=True)

    duplicate_a = "The deploy pipeline broke due to a configuration change last night."
    duplicate_b = "Last night, the deploy pipeline broke due to a configuration change."
    distinct = "We also need to review the configuration change process for the deploy pipeline."

    assert duplicate_a in without_suppression.sentences
    assert duplicate_b in without_suppression.sentences
    assert distinct not in without_suppression.sentences

    assert distinct in with_suppression.sentences
    assert not (duplicate_a in with_suppression.sentences and duplicate_b in with_suppression.sentences), (
        "redundancy suppression should not let both near-duplicate sentences through"
    )

    assert with_suppression.sentences != without_suppression.sentences


def test_summary_preserves_original_transcript_order():
    result = summarize(SIGNAL_VS_FILLER, top_k=3)
    all_sentences = split_sentences(SIGNAL_VS_FILLER)
    indices = [all_sentences.index(s) for s in result.sentences]
    assert indices == sorted(indices)


def test_summarize_is_deterministic():
    first = summarize(SIGNAL_VS_FILLER, top_k=3)
    second = summarize(SIGNAL_VS_FILLER, top_k=3)
    assert first.sentences == second.sentences
    assert [r.score for r in first.ranked] == [r.score for r in second.ranked]


def test_single_sentence_transcript_returns_that_sentence():
    result = summarize("Just one sentence here.", top_k=3)
    assert result.sentences == ["Just one sentence here."]


def test_top_k_larger_than_sentence_count_returns_all_sentences():
    text = "First point. Second point. Third point."
    result = summarize(text, top_k=10)
    assert len(result.sentences) == 3


def test_empty_text_raises_value_error():
    with pytest.raises(ValueError):
        summarize("   ")
