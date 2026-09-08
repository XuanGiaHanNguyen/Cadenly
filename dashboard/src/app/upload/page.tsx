"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Home, ChevronRight, UploadCloud, FileText, ClipboardPaste, CheckCircle2, Loader2, Sparkles } from "lucide-react";
import { Sidebar } from "@/components/Sidebar";
import { TopBar } from "@/components/TopBar";
import { useAppShell } from "@/hooks/useAppShell";

type Stage = "input" | "processing" | "done";
type InputMode = "file" | "text";

/* The exact CARD_THEMES palette from dashboard/page.tsx (bg-100/text-900/bar
   shade per color) - reused as-is, no new colors invented here. */
const STAGE_THEMES = [
  { bg: "bg-orange-100", text: "text-orange-900", solid: "bg-orange-300" },
  { bg: "bg-violet-100", text: "text-violet-900", solid: "bg-violet-300" },
  { bg: "bg-lime-100", text: "text-lime-900", solid: "bg-lime-400" },
  { bg: "bg-sky-100", text: "text-sky-900", solid: "bg-sky-300" },
];

const PIPELINE_STAGES = [
  "Uploading notes",
  "Transcribing content",
  "Extracting action items",
  "Matching people",
  "Scheduling tasks",
];

const STAGE_DELAY_MS = 850;

const PREVIEW_TASKS = [
  { title: "Send follow-up email to client", person: "Sarah Kim", theme: STAGE_THEMES[0] },
  { title: "Prepare slide deck for review", person: "John", theme: STAGE_THEMES[1] },
  { title: "Schedule 1:1 with Priya", person: "Priya", theme: STAGE_THEMES[2] },
  { title: "Update project timeline", person: "Sarah Kim", theme: STAGE_THEMES[3] },
];

const STAGE_STEP_INDEX: Record<Stage, number> = { input: 0, processing: 1, done: 2 };

/**
 * UI-only mock for now - no upload actually reaches scheduler-engine yet.
 * The staged pipeline below is a fixed timer, not real progress, so this can
 * be signed off on before the backend endpoint exists.
 */
export default function UploadNotesPage() {
  const { user, checkingAuth, connected, liveEvents, personName, simulateBooking, logout } = useAppShell();

  const [search, setSearch] = useState("");
  const [stage, setStage] = useState<Stage>("input");
  const [inputMode, setInputMode] = useState<InputMode>("file");
  const [file, setFile] = useState<File | null>(null);
  const [notesText, setNotesText] = useState("");
  const [stepIndex, setStepIndex] = useState(0);
  const [dragOver, setDragOver] = useState(false);

  useEffect(() => {
    if (stage !== "processing") return;
    if (stepIndex >= PIPELINE_STAGES.length) {
      setStage("done");
      return;
    }
    const timer = setTimeout(() => setStepIndex((i) => i + 1), STAGE_DELAY_MS);
    return () => clearTimeout(timer);
  }, [stage, stepIndex]);

  const sourceName = inputMode === "file" ? file?.name ?? "your file" : "your pasted notes";
  const canContinue = inputMode === "file" ? file != null : notesText.trim().length > 0;

  function pickFile(f: File | null) {
    if (f) setFile(f);
  }

  function startPipeline() {
    setStepIndex(0);
    setStage("processing");
  }

  function reset() {
    setStage("input");
    setFile(null);
    setNotesText("");
    setStepIndex(0);
  }

  if (checkingAuth) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-neutral-50 text-sm text-neutral-500">
        Checking session…
      </div>
    );
  }

  const currentStep = STAGE_STEP_INDEX[stage];

  return (
    <div className="flex min-h-screen bg-neutral-50 text-neutral-900">
      <Sidebar active="upload" />

      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar
          search={search}
          onSearchChange={setSearch}
          searchPlaceholder="Search"
          user={user}
          connected={connected}
          liveEvents={liveEvents}
          personName={personName}
          onSimulateBooking={simulateBooking}
          onLogout={logout}
        />

        <main className="flex-1 p-8">
          <div className="mx-auto space-y-5">
            {/* Breadcrumb */}
            <div className="flex items-center gap-1.5 text-sm text-neutral-400">
              <Link href="/dashboard" className="flex h-6 w-6 items-center justify-center rounded hover:bg-neutral-100 hover:text-neutral-600">
                <Home size={14} />
              </Link>
              <ChevronRight size={14} />
              <span>Cadenly</span>
              <ChevronRight size={14} />
              <span className="rounded-md border border-neutral-200 px-2 py-0.5 font-medium text-neutral-900">Upload notes</span>
            </div>

            <div>
              <h1 className="text-2xl font-semibold">Upload meeting notes</h1>
              <p className="mt-1 text-sm text-neutral-500">
                Drop in your notes and Cadenly pulls out action items and schedules them for the right people.
              </p>
            </div>

            {/* Step progress */}
            <div className="flex items-center gap-2">
              {[0, 1, 2].map((i) => (
                <div key={i} className={`h-1.5 flex-1 rounded-full ${i <= currentStep ? "bg-sky-300" : "bg-neutral-200"}`} />
              ))}
            </div>

            {stage === "input" && (
              <section className="rounded-2xl border border-neutral-200 bg-white p-6">
                <h2 className="mb-4 flex items-center gap-2 text-base font-semibold">
                  <Sparkles size={16} className="text-neutral-900" /> Step 1 · Add your notes
                </h2>

                <div className="mb-4 inline-flex items-center gap-1 rounded-lg bg-neutral-100 p-1 text-sm">
                  <button
                    onClick={() => setInputMode("file")}
                    className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 font-medium ${
                      inputMode === "file" ? "bg-white text-neutral-900 shadow-sm" : "text-neutral-500 hover:text-neutral-700"
                    }`}
                  >
                    <UploadCloud size={14} /> Upload file
                  </button>
                  <button
                    onClick={() => setInputMode("text")}
                    className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 font-medium ${
                      inputMode === "text" ? "bg-white text-neutral-900 shadow-sm" : "text-neutral-500 hover:text-neutral-700"
                    }`}
                  >
                    <ClipboardPaste size={14} /> Paste text
                  </button>
                </div>

                {inputMode === "file" ? (
                  <label
                    onDragOver={(e) => {
                      e.preventDefault();
                      setDragOver(true);
                    }}
                    onDragLeave={() => setDragOver(false)}
                    onDrop={(e) => {
                      e.preventDefault();
                      setDragOver(false);
                      pickFile(e.dataTransfer.files[0] ?? null);
                    }}
                    className={`flex min-h-[220px] cursor-pointer flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed p-8 text-center transition ${
                      dragOver || file
                        ? "border-sky-300 bg-sky-50"
                        : "border-neutral-200 bg-neutral-50 hover:border-neutral-300"
                    }`}
                  >
                    <input
                      type="file"
                      accept=".txt,.md,.doc,.docx,.pdf,.vtt"
                      className="hidden"
                      onChange={(e) => pickFile(e.target.files?.[0] ?? null)}
                    />
                    {file ? (
                      <>
                        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-white">
                          <FileText className="h-6 w-6 text-sky-700" />
                        </div>
                        <span className="text-sm font-medium text-neutral-900">{file.name}</span>
                        <span className="text-xs text-neutral-400">Click to choose a different file</span>
                      </>
                    ) : (
                      <>
                        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-white">
                          <UploadCloud className="h-6 w-6 text-neutral-500" />
                        </div>
                        <span className="text-sm font-medium text-neutral-700">Click to upload or drag a file in</span>
                        <span className="text-xs text-neutral-400">.txt, .docx, .pdf, .vtt</span>
                      </>
                    )}
                  </label>
                ) : (
                  <textarea
                    value={notesText}
                    onChange={(e) => setNotesText(e.target.value)}
                    placeholder="Paste your meeting notes here…"
                    className="min-h-[220px] w-full resize-y rounded-xl border border-neutral-200 bg-neutral-50 p-4 text-sm outline-none focus:border-sky-300 focus:bg-white"
                  />
                )}

                <button
                  disabled={!canContinue}
                  onClick={startPipeline}
                  className="mt-4 w-full rounded-lg bg-sky-300 px-4 py-2.5 text-sm font-semibold text-white hover:bg-sky-200 disabled:opacity-40"
                >
                  Schedule from notes
                </button>
              </section>
            )}

            {stage === "processing" && (
              <section className="rounded-2xl border border-neutral-200 bg-white p-6">
                <h2 className="mb-5 text-base font-semibold">Step 2 · Running the pipeline</h2>

                <div className="space-y-3">
                  {PIPELINE_STAGES.map((label, i) => {
                    const theme = STAGE_THEMES[i % STAGE_THEMES.length];
                    const isDone = i < stepIndex;
                    const isCurrent = i === stepIndex;
                    return (
                      <div key={label} className="flex items-center gap-3">
                        <div
                          className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${
                            isDone ? theme.solid : isCurrent ? theme.bg : "bg-neutral-100"
                          }`}
                        >
                          {isDone ? (
                            <CheckCircle2 className="h-4 w-4 text-white" />
                          ) : isCurrent ? (
                            <Loader2 className={`h-4 w-4 animate-spin ${theme.text}`} />
                          ) : (
                            <div className="h-2 w-2 rounded-full bg-neutral-300" />
                          )}
                        </div>
                        <span className={`text-sm ${isDone || isCurrent ? "font-medium text-neutral-900" : "text-neutral-400"}`}>
                          {label}
                        </span>
                      </div>
                    );
                  })}

                  <div className="mt-1 flex gap-1 pt-1">
                    {PIPELINE_STAGES.map((_, i) => {
                      const theme = STAGE_THEMES[i % STAGE_THEMES.length];
                      return (
                        <div key={i} className="h-1.5 flex-1 overflow-hidden rounded-full bg-neutral-100">
                          <div
                            className={`h-1.5 rounded-full transition-all duration-500 ${i <= stepIndex ? theme.solid : ""}`}
                            style={{ width: i < stepIndex ? "100%" : i === stepIndex ? "60%" : "0%" }}
                          />
                        </div>
                      );
                    })}
                  </div>
                </div>
              </section>
            )}

            {stage === "done" && (
              <section className="rounded-2xl border border-emerald-200 bg-emerald-50 p-6">
                <div className="mb-1 flex items-center gap-2">
                  <CheckCircle2 className="h-5 w-5 text-emerald-600" />
                  <h2 className="text-base font-semibold text-emerald-900">Step 3 · Notes processed</h2>
                </div>
                <p className="mb-4 text-sm text-emerald-800">
                  This is a UI preview - nothing was actually scheduled yet. Once the backend pipeline is wired up,
                  action items from {sourceName} will land here as real scheduled tasks. Here&apos;s a preview of
                  what that could look like:
                </p>

                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  {PREVIEW_TASKS.map((task) => (
                    <div key={task.title} className={`flex items-start gap-3 rounded-xl ${task.theme.bg} p-3`}>
                      <span className={`mt-1 h-2 w-2 shrink-0 rounded-full ${task.theme.solid}`} />
                      <div className="min-w-0">
                        <p className={`truncate text-sm font-medium ${task.theme.text}`}>{task.title}</p>
                        <p className="text-xs text-neutral-500">{task.person}</p>
                      </div>
                    </div>
                  ))}
                </div>

                <button
                  onClick={reset}
                  className="mt-5 w-full rounded-lg border border-emerald-300 bg-white px-4 py-2.5 text-sm font-medium text-emerald-800 hover:bg-emerald-100"
                >
                  Upload another
                </button>
              </section>
            )}
          </div>
        </main>
      </div>
    </div>
  );
}
