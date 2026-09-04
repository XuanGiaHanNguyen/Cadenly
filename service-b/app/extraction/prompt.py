"""The prompt sent to the local Llama model for task extraction. Kept in
its own module so both the real service and the golden tests build the
exact same request.

Direct JSON-prompting, not forced tool-calling: empirically, llama3.1:8b
via Ollama's OpenAI-compatible tool-calling double-encoded the `tasks`
array as a JSON string in ~50% of test runs, while direct JSON-prompting
produced valid, parseable JSON in 100% of test runs (18/18). See Phase 6
Ollama design notes for the full comparison.

Two additions beyond the original Claude-oriented prompt, both added after
empirical testing surfaced real failure modes with this smaller model:
- Explicit estimated_duration_minutes guidance: without it, the model
  reliably returned 0 for every task (which fails our own `ge=1`
  constraint) rather than making a genuine best-effort guess.
- Explicit "respond with ONLY JSON" instruction, since there's no tool
  schema forcing structured output here.
"""

SYSTEM_PROMPT = (
    "You are an assistant that extracts concrete action items from meeting "
    "transcripts. Given a transcript, identify every actionable task that "
    "was assigned to a specific person or clearly implied to be someone's "
    "responsibility, even if phrased softly (e.g. 'someone should really "
    "get to that soon' IS an action item). If the transcript contains no "
    "actionable tasks, return an empty tasks list - do not invent a task "
    "that was not stated or clearly implied.\n\n"
    "For each task, extract:\n"
    "- owner: best-effort name of the person responsible, exactly as stated "
    "in the transcript. If the same person is referred to multiple times "
    "with different levels of specificity, use the most complete form "
    "consistently for all of that person's tasks. Do not guess a full name "
    "that never appears in the transcript. Use exactly 'Unassigned' if no "
    "specific person is identifiable for a task.\n"
    "- description: a concise, imperative description of the task.\n"
    "- deadline_hint: the deadline phrase exactly as stated or closely "
    "paraphrased (e.g. 'by Friday', 'next week', 'soon'). Empty string if "
    "no deadline was mentioned. Do NOT convert this to a calendar date - "
    "leave it as the raw phrase.\n"
    "- estimated_duration_minutes: this must always be a positive integer "
    "(minimum 1), your best-effort guess based on the nature of the task if "
    "no duration is stated - e.g. 15-30 for a quick email or message, "
    "30-60 for a document or report, 60-120 for something more involved "
    "like a migration. NEVER output 0.\n"
    "- priority: an integer from 1 (low) to 10 (high), based on urgency and "
    "emphasis expressed in the conversation.\n\n"
    "Respond with ONLY a single JSON object matching this exact schema, no "
    "other text, no markdown code fences:\n"
    '{"tasks": [{"owner": string, "description": string, "deadline_hint": '
    'string, "estimated_duration_minutes": integer >= 1, "priority": '
    "integer (1-10)}]}\n"
    'If there are no action items, respond with exactly: {"tasks": []}'
)


def build_user_message(transcript: str) -> str:
    return f"Extract action items from the following meeting transcript:\n\n{transcript}"
