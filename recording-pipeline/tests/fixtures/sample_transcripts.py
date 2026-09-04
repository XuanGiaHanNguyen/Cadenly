"""Hand-crafted sample transcripts used to validate summary *quality*,
not just "it ran without crashing". Filler sentences are deliberately
written to share no vocabulary with each other or with the signal
sentences - any accidental word overlap (even a word as small as
"everyone" showing up twice) creates a real edge in the similarity graph
and can pull a filler sentence's score up, which defeats the point of the
fixture. Verified empirically against the actual textrank implementation,
not just by inspection."""

# Central topic (deploy pipeline outage) mentioned/paraphrased across three
# sentences; the rest is small talk that shares no vocabulary with the
# signal cluster or with each other.
SIGNAL_VS_FILLER = " ".join(
    [
        "Good morning, glad you could join.",
        "Is the connection stable for everybody?",
        "The deploy pipeline has been broken since yesterday's release.",
        "We believe the broken deploy pipeline is caused by a bad configuration change.",
        "Let's briefly check on the office snack budget for this quarter.",
        "Someone should roll back the configuration change to fix the deploy pipeline today.",
        "Alright, take care and catch you later.",
    ]
)
SIGNAL_SENTENCE_PREFIXES = (
    "The deploy pipeline has been broken",
    "We believe the broken deploy pipeline",
    "Someone should roll back the configuration change",
)
FILLER_SENTENCE_PREFIXES = (
    "Good morning, glad you could join",
    "Is the connection stable",
    "Let's briefly check on the office snack budget",
    "Alright, take care",
)

# Sentences 0 and 1 are near-duplicates of each other (same fact, reworded).
# Sentence 2 is a distinct, related point sharing partial vocabulary with
# 0/1. Sentences 3 and 4 are isolated filler. A 5-sentence corpus (rather
# than 4) matters here: with 4 sentences, "deploy"/"pipeline"/
# "configuration"/"change" each appear in exactly 3 of 4 documents, which
# hits idf = ln(n/(1+df)) = ln(4/4) = 0 - i.e. those shared terms
# contribute *zero* to cosine similarity, wiping out sentence 2's real
# topical overlap with 0/1. Adding a 5th sentence shifts df relative to n
# so those terms get a small positive idf instead.
REDUNDANCY = " ".join(
    [
        "The deploy pipeline broke due to a configuration change last night.",
        "Last night, the deploy pipeline broke due to a configuration change.",
        "We also need to review the configuration change process for the deploy pipeline.",
        "Thanks everyone for joining today.",
        "Let's grab lunch after this meeting.",
    ]
)
