"""
Resolves a raw deadline_hint phrase (e.g. "by Friday", "next week", "soon")
into a concrete datetime, relative to the meeting's recording timestamp -
not "now" at resolution time, since resolution can happen well after the
meeting.

Two things had to be empirically discovered, not assumed:

1. dateparser's DEFAULT behavior resolves weekday names to the most recent
   PAST occurrence relative to the reference date ("by Friday" from a
   Tuesday base resolved to the Friday before, not the Friday ahead) - a
   real bug for deadline resolution, since a deadline in the past is
   nonsensical. Fixed with PREFER_DATES_FROM: 'future'.

2. Real extraction output (see Phase 6 golden tests) produces compound
   phrases like "next week's board review", which dateparser.parse()
   cannot parse as a whole (it expects the full string to be a date
   expression) and dateparser.search_dates() also fails on (and can
   produce false-positive matches on unrelated words in longer text, e.g.
   matching "we" as a date). The targeted fix here is stripping a trailing
   possessive clause ("'s board review") before parsing - this directly
   handles the actual observed pattern; it does not attempt general NLP
   date extraction from arbitrary text.
"""

import re
from datetime import datetime, timedelta

import dateparser

_POSSESSIVE_TAIL_RE = re.compile(r"'s\b.*$")

# Conservative fallback for genuinely unresolvable phrases ("soon", "ASAP",
# or no deadline mentioned at all). Short, not distant: a far-out fallback
# would make WeightCalculator's urgency term treat a "soon" task as low
# urgency, inverting what the speaker meant. See Phase 7 design notes.
FALLBACK_BUFFER = timedelta(days=3)


def resolve_deadline(deadline_hint: str, recording_timestamp: datetime) -> datetime:
    settings = {"RELATIVE_BASE": recording_timestamp, "PREFER_DATES_FROM": "future"}

    resolved = dateparser.parse(deadline_hint, settings=settings)
    if resolved is None:
        stripped = _POSSESSIVE_TAIL_RE.sub("", deadline_hint).strip()
        if stripped and stripped != deadline_hint:
            resolved = dateparser.parse(stripped, settings=settings)

    if resolved is None:
        return recording_timestamp + FALLBACK_BUFFER

    if resolved.tzinfo is None:
        resolved = resolved.replace(tzinfo=recording_timestamp.tzinfo)

    return resolved
