"""Golden transcripts for the real-LLM extraction tests (@pytest.mark.slow)
and the manual Haiku-vs-Sonnet comparison script. Assertions against these
must stay loose (structural, not exact-match) since LLM output isn't
deterministic like TextRank's."""

CLEAR_ACTION_ITEMS = (
    "Alright, let's go over the open items. John, can you send the Q3 report "
    "to finance? John: Yeah, I'll have that done by Friday. Sarah, we also "
    "need the slides updated before next week's board review. Sarah: Got it, "
    "I'll take care of the slides."
)

NO_ACTION_ITEMS = (
    "How's everyone doing today? Good, thanks. Nice weekend? Yeah, went "
    "hiking, it was great. Anyway, good to see everyone, have a good rest "
    "of your week, see you all next time."
)

VAGUE_DEADLINE = (
    "We've been putting off the database migration for a while now. "
    "Someone really should get to that soon, it's becoming a bigger risk "
    "the longer we wait."
)
