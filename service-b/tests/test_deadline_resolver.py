from datetime import datetime, timedelta, timezone

from app.scheduling.deadline_resolver import FALLBACK_BUFFER, resolve_deadline

# A Tuesday - picked deliberately so "Friday"/"next week" have unambiguous
# expected answers, and so the past-vs-future weekday bug (see module
# docstring) would be caught if it regressed.
BASE = datetime(2026, 9, 1, 9, 0, 0, tzinfo=timezone.utc)


def test_resolves_weekday_to_the_upcoming_occurrence_not_the_past_one():
    # Empirically discovered: dateparser's default behavior resolves "Friday"
    # to the most recent PAST Friday relative to the base. PREFER_DATES_FROM
    # future must be set, or this test fails by landing before BASE.
    resolved = resolve_deadline("by Friday", BASE)
    assert resolved > BASE
    assert resolved.strftime("%A") == "Friday"


def test_resolves_next_week_relative_to_recording_timestamp():
    resolved = resolve_deadline("next week", BASE)
    assert resolved > BASE
    assert timedelta(days=6) <= (resolved - BASE) <= timedelta(days=8)


def test_strips_possessive_tail_to_resolve_compound_phrase():
    # Real observed extraction output (Phase 6 golden tests) produces
    # exactly this shape: a deadline phrase with a trailing possessive clause.
    resolved = resolve_deadline("before next week's board review", BASE)
    assert resolved > BASE
    assert timedelta(days=6) <= (resolved - BASE) <= timedelta(days=8)


def test_unresolvable_phrase_falls_back_to_conservative_buffer():
    resolved = resolve_deadline("soon", BASE)
    assert resolved == BASE + FALLBACK_BUFFER


def test_empty_hint_falls_back_to_conservative_buffer():
    resolved = resolve_deadline("", BASE)
    assert resolved == BASE + FALLBACK_BUFFER


def test_resolved_deadline_is_timezone_aware():
    resolved = resolve_deadline("by Friday", BASE)
    assert resolved.tzinfo is not None
