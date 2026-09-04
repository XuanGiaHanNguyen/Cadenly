"""
Hand-implemented TextRank summarizer.

Pipeline: split into sentences -> tokenize + remove stopwords -> TF-IDF
vectors -> cosine-similarity graph -> PageRank-style power iteration over
that graph -> top-k sentence selection (with optional redundancy
suppression), reordered back into original transcript order.

numpy is used for vector/matrix arithmetic (the same role Java's Math/
PriorityQueue play elsewhere in this project) - TF-IDF, cosine similarity,
graph construction, and the PageRank iteration are all implemented here,
not delegated to a summarization library.
"""

from __future__ import annotations

import re
from collections import Counter
from dataclasses import dataclass

import numpy as np

from app.summarization.stopwords import STOPWORDS

_SENTENCE_SPLIT_RE = re.compile(r"(?<=[.!?])\s+")
_TOKEN_RE = re.compile(r"[a-z0-9']+")


@dataclass(frozen=True)
class RankedSentence:
    index: int
    text: str
    score: float


@dataclass(frozen=True)
class SummaryResult:
    sentences: list[str]  # selected summary sentences, in original transcript order
    ranked: list[RankedSentence]  # every sentence with its PageRank score, for inspection


def split_sentences(text: str) -> list[str]:
    """Regex-based split, not NLTK's punkt - avoids a corpus download
    dependency so tests don't need network access. Mishandles abbreviations
    like "Mr. Smith" occasionally; acceptable for meeting-transcript text."""
    stripped = text.strip()
    if not stripped:
        return []
    parts = _SENTENCE_SPLIT_RE.split(stripped)
    return [p.strip() for p in parts if p.strip()]


def tokenize(sentence: str) -> list[str]:
    tokens = _TOKEN_RE.findall(sentence.lower())
    return [t for t in tokens if t not in STOPWORDS]


def compute_tfidf_vectors(tokenized_sentences: list[list[str]]) -> np.ndarray:
    n = len(tokenized_sentences)
    vocabulary = sorted({token for tokens in tokenized_sentences for token in tokens})
    vocab_index = {term: i for i, term in enumerate(vocabulary)}

    doc_freq = np.zeros(len(vocabulary))
    for tokens in tokenized_sentences:
        for term in set(tokens):
            doc_freq[vocab_index[term]] += 1
    idf = np.log(n / (1.0 + doc_freq))

    vectors = np.zeros((n, len(vocabulary)))
    for i, tokens in enumerate(tokenized_sentences):
        if not tokens:
            continue
        counts = Counter(tokens)
        length = len(tokens)
        for term, count in counts.items():
            j = vocab_index[term]
            vectors[i, j] = (count / length) * idf[j]
    return vectors


def cosine_similarity_matrix(vectors: np.ndarray) -> np.ndarray:
    norms = np.linalg.norm(vectors, axis=1, keepdims=True)
    safe_norms = np.where(norms == 0, 1.0, norms)  # zero vectors stay all-zero after "normalizing"
    normalized = vectors / safe_norms
    sim = normalized @ normalized.T
    np.fill_diagonal(sim, 0.0)
    return sim


def build_similarity_graph(vectors: np.ndarray, epsilon: float = 1e-9) -> np.ndarray:
    """Undirected weighted graph: sentences sharing no vocabulary get an
    edge weight of exactly 0 (treated as absent), sparsifying the graph."""
    sim = cosine_similarity_matrix(vectors)
    sim[sim < epsilon] = 0.0
    return sim


def pagerank(
    weights: np.ndarray,
    damping: float = 0.85,
    max_iterations: int = 100,
    convergence_threshold: float = 1e-4,
) -> np.ndarray:
    """Weighted TextRank/PageRank power iteration (Mihalcea & Tarau 2004):

        WS(Vi) = (1-d) + d * sum_{j in In(Vi)} [w(j,i) / sum_out(Vj)] * WS(Vj)

    Edges only exist where weight > 0, so a node never appears as an
    in-neighbor with a zero out-sum denominator - no divide-by-zero special
    case is needed for that. Isolated nodes (sum_out == 0) simply receive
    no contribution from anyone, which the `safe_out_sums` guard below
    handles directly.
    """
    n = weights.shape[0]
    if n == 0:
        return np.array([])
    if n == 1:
        return np.array([1.0 - damping])

    scores = np.full(n, 1.0 / n)
    out_sums = weights.sum(axis=1)
    safe_out_sums = np.where(out_sums == 0, 1.0, out_sums)
    transition = weights / safe_out_sums[:, None]  # transition[j, i] = w(j,i) / sum_out(j)

    for _ in range(max_iterations):
        new_scores = (1.0 - damping) + damping * (transition.T @ scores)
        delta = float(np.sum(np.abs(new_scores - scores)))
        scores = new_scores
        if delta < convergence_threshold:
            break
    return scores


def select_top_k(
    scores: np.ndarray,
    similarity: np.ndarray,
    top_k: int,
    redundancy_suppression: bool = True,
    redundancy_threshold: float = 0.8,
) -> list[int]:
    n = len(scores)
    top_k = min(top_k, n)
    ranked_indices = list(np.argsort(-scores))

    if not redundancy_suppression:
        return sorted(ranked_indices[:top_k])

    selected: list[int] = []
    for idx in ranked_indices:
        if len(selected) >= top_k:
            break
        if any(similarity[idx, chosen] >= redundancy_threshold for chosen in selected):
            continue
        selected.append(int(idx))

    # If redundancy suppression filtered out too many candidates (e.g.
    # everything left is mutually similar), backfill with the next-highest
    # ranked remaining sentences so the summary length contract holds.
    if len(selected) < top_k:
        for idx in ranked_indices:
            if len(selected) >= top_k:
                break
            if int(idx) not in selected:
                selected.append(int(idx))

    return sorted(selected)


def summarize(
    text: str,
    top_k: int = 3,
    redundancy_suppression: bool = True,
    damping: float = 0.85,
    max_iterations: int = 100,
    convergence_threshold: float = 1e-4,
    redundancy_threshold: float = 0.8,
) -> SummaryResult:
    sentences = split_sentences(text)
    if not sentences:
        raise ValueError("text must contain at least one sentence")

    tokenized = [tokenize(s) for s in sentences]
    vectors = compute_tfidf_vectors(tokenized)
    similarity = build_similarity_graph(vectors)
    scores = pagerank(
        similarity,
        damping=damping,
        max_iterations=max_iterations,
        convergence_threshold=convergence_threshold,
    )

    selected_indices = select_top_k(
        scores, similarity, top_k, redundancy_suppression, redundancy_threshold
    )

    ranked = [
        RankedSentence(index=i, text=sentences[i], score=float(scores[i]))
        for i in range(len(sentences))
    ]
    summary_sentences = [sentences[i] for i in selected_indices]

    return SummaryResult(sentences=summary_sentences, ranked=ranked)
