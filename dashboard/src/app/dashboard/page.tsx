"use client";

import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Calendar, Plus } from "lucide-react";
import { apiFetch } from "@/lib/api";
import { Sidebar } from "@/components/Sidebar";
import { TopBar } from "@/components/TopBar";
import { useAppShell } from "@/hooks/useAppShell";

const DURATION_CAP_MINUTES = 180;

type PlacedTask = { description: string; owner: string; start: string; end: string };
type RejectedTask = { description: string; owner: string; reason: string };
type UnresolvedTask = { ownerNameRaw: string; description: string; reason: string };
type TaskBoard = { placed: PlacedTask[]; rejected: RejectedTask[]; unresolved: UnresolvedTask[] };

type TabKey = "all" | "placed" | "rejected" | "unresolved";

type CardItem = {
  key: string;
  kind: "placed" | "rejected" | "unresolved";
  badgeText: string;
  title: string;
  subtitle: string;
  durationMinutes?: number;
  caption?: string;
};

const CARD_THEMES = [
  { bg: "bg-orange-100", badge: "bg-orange-200 text-orange-900", bar: "bg-orange-300" },
  { bg: "bg-violet-100", badge: "bg-violet-200 text-violet-900", bar: "bg-violet-300" },
  { bg: "bg-lime-100", badge: "bg-lime-200 text-lime-900", bar: "bg-lime-400" },
  { bg: "bg-sky-100", badge: "bg-sky-200 text-sky-900", bar: "bg-sky-300" },
];

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

const TABS: { key: TabKey; label: string }[] = [
  { key: "all", label: "All" },
  { key: "placed", label: "Placed" },
  { key: "rejected", label: "Rejected" },
  { key: "unresolved", label: "Unresolved" },
];

function initials(name: string): string {
  return name
    .split(" ")
    .map((part) => part[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();
}

function formatDateShort(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" });
}

function isSameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

export default function DashboardPage() {
  const { user, checkingAuth, people, peopleError, personName, connected, liveEvents, simulateBooking, logout } =
    useAppShell();

  const [board, setBoard] = useState<TaskBoard>({ placed: [], rejected: [], unresolved: [] });
  const [loadError, setLoadError] = useState<string | null>(null);

  const [formOpen, setFormOpen] = useState(false);
  const [formPerson, setFormPerson] = useState("");
  const [formDescription, setFormDescription] = useState("");
  const [formDeadline, setFormDeadline] = useState("");
  const [formPriority, setFormPriority] = useState(5);
  const [formDuration, setFormDuration] = useState(30);
  const [formSubmitting, setFormSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const [activeTab, setActiveTab] = useState<TabKey>("all");
  const [search, setSearch] = useState("");

  async function loadTasks() {
    const response = await apiFetch("/api/tasks");
    if (!response.ok) throw new Error("failed to load tasks");
    setBoard(await response.json());
  }

  useEffect(() => {
    if (checkingAuth) return;
    loadTasks().catch(() => setLoadError("Could not reach scheduler-engine on localhost:8080 - is it running?"));
  }, [checkingAuth]);

  const weekdayCounts = useMemo(() => {
    const counts = new Array(7).fill(0);
    for (const task of board.placed) {
      counts[new Date(task.start).getDay()] += 1;
    }
    return counts;
  }, [board.placed]);

  const maxWeekdayCount = Math.max(1, ...weekdayCounts);
  const todayIndex = new Date().getDay();

  const weekDates = useMemo(() => {
    const today = new Date();
    const startOfWeek = new Date(today);
    startOfWeek.setDate(today.getDate() - today.getDay());
    return WEEKDAYS.map((_, i) => {
      const d = new Date(startOfWeek);
      d.setDate(startOfWeek.getDate() + i);
      return d;
    });
  }, []);

  const upcoming = useMemo(
    () =>
      [...board.placed].sort((a, b) => new Date(a.start).getTime() - new Date(b.start).getTime()).slice(0, 5),
    [board.placed],
  );

  const cardsByTab = useMemo(() => {
    const placedItems: CardItem[] = [...board.placed]
      .sort((a, b) => new Date(a.start).getTime() - new Date(b.start).getTime())
      .map((t, i) => ({
        key: `placed-${i}`,
        kind: "placed" as const,
        badgeText: `Due ${formatDateShort(t.start)}`,
        title: t.description,
        subtitle: `${personName(t.owner)} · ${formatTime(t.start)}–${formatTime(t.end)}`,
        durationMinutes: Math.round((new Date(t.end).getTime() - new Date(t.start).getTime()) / 60_000),
      }));
    const rejectedItems: CardItem[] = board.rejected.map((t, i) => ({
      key: `rejected-${i}`,
      kind: "rejected" as const,
      badgeText: "Rejected",
      title: t.description,
      subtitle: personName(t.owner),
      caption: t.reason,
    }));
    const unresolvedItems: CardItem[] = board.unresolved.map((t, i) => ({
      key: `unresolved-${i}`,
      kind: "unresolved" as const,
      badgeText: "Unresolved",
      title: t.description,
      subtitle: `${t.ownerNameRaw} · person not recognized`,
      caption: t.reason,
    }));

    const source: CardItem[] =
      activeTab === "all"
        ? [...placedItems, ...rejectedItems, ...unresolvedItems]
        : activeTab === "placed"
          ? placedItems
          : activeTab === "rejected"
            ? rejectedItems
            : unresolvedItems;

    const q = search.trim().toLowerCase();
    const filtered = q
      ? source.filter((c) => c.title.toLowerCase().includes(q) || c.subtitle.toLowerCase().includes(q))
      : source;

    return filtered.slice(0, 8);
  }, [activeTab, board, personName, search]);

  const badgeColor: Record<CardItem["kind"], string> = {
    placed: "",
    rejected: "bg-rose-200 text-rose-900",
    unresolved: "bg-amber-200 text-amber-900",
  };

  async function submitTask(e: FormEvent) {
    e.preventDefault();
    setFormSubmitting(true);
    setFormError(null);

    try {
      const response = await apiFetch("/api/tasks/submit", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          tasks: [
            {
              ownerName: formPerson,
              description: formDescription,
              deadline: new Date(formDeadline).toISOString(),
              priority: formPriority,
              estimatedDurationMinutes: formDuration,
            },
          ],
        }),
      });
      const result: TaskBoard = await response.json();

      if (result.unresolved.length > 0) {
        setFormError(`Person not recognized: ${result.unresolved[0].reason}`);
      } else if (result.rejected.length > 0) {
        setFormError(`Rejected: ${result.rejected[0].reason}`);
      } else {
        setFormOpen(false);
        setFormDescription("");
        setFormDeadline("");
      }
      await loadTasks();
    } catch {
      setFormError("Could not reach scheduler-engine on localhost:8080");
    } finally {
      setFormSubmitting(false);
    }
  }

  if (checkingAuth) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-neutral-50 text-sm text-neutral-500">
        Checking session…
      </div>
    );
  }

  return (
    <div className="flex min-h-screen bg-neutral-50 text-neutral-900">
      <Sidebar active="dashboard" />

      {/* Main content */}
      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar
          search={search}
          onSearchChange={setSearch}
          searchPlaceholder="Search tasks"
          user={user}
          connected={connected}
          liveEvents={liveEvents}
          personName={personName}
          onSimulateBooking={simulateBooking}
          onLogout={logout}
        />

        <main className="flex-1 p-8">
          <div className="mx-auto max-w-6xl space-y-6">
            <h1 className="text-2xl font-semibold">Dashboard</h1>

            {(loadError || peopleError) && (
              <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                {loadError ?? peopleError}
              </div>
            )}

            {/* Task cards */}
            <section className="rounded-2xl border border-neutral-200 bg-white p-5">
              <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-4">
                  <h2 className="text-base font-semibold">My Tasks</h2>
                  <div className="flex items-center gap-1.5">
                    {TABS.map((tab) => (
                      <button
                        key={tab.key}
                        onClick={() => setActiveTab(tab.key)}
                        className={`rounded-full px-3 py-1 text-xs font-medium ${
                          activeTab === tab.key ? "bg-neutral-900 text-white" : "bg-neutral-100 text-neutral-600 hover:bg-neutral-200"
                        }`}
                      >
                        {tab.label}
                      </button>
                    ))}
                  </div>
                </div>
                <button
                  onClick={() => setFormOpen((v) => !v)}
                  className="flex items-center gap-1 rounded-lg bg-brand-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-brand-800"
                >
                  <Plus size={14} /> {formOpen ? "Cancel" : "Add task"}
                </button>
              </div>

              {formOpen && (
                <form onSubmit={submitTask} className="mb-5 grid grid-cols-2 gap-3 rounded-xl bg-neutral-50 p-4 text-sm">
                  <select
                    required
                    value={formPerson}
                    onChange={(e) => setFormPerson(e.target.value)}
                    className="col-span-1 rounded-lg border border-neutral-300 px-3 py-2"
                  >
                    <option value="" disabled>
                      Person
                    </option>
                    {people.map((p) => (
                      <option key={p.id} value={p.name}>
                        {p.name}
                      </option>
                    ))}
                  </select>
                  <input
                    required
                    type="datetime-local"
                    value={formDeadline}
                    onChange={(e) => setFormDeadline(e.target.value)}
                    className="col-span-1 rounded-lg border border-neutral-300 px-3 py-2"
                  />
                  <input
                    required
                    placeholder="Description"
                    value={formDescription}
                    onChange={(e) => setFormDescription(e.target.value)}
                    className="col-span-2 rounded-lg border border-neutral-300 px-3 py-2"
                  />
                  <label className="col-span-1 flex items-center gap-2 text-neutral-600">
                    Priority
                    <input
                      type="number"
                      min={1}
                      max={10}
                      value={formPriority}
                      onChange={(e) => setFormPriority(Number(e.target.value))}
                      className="w-16 rounded-lg border border-neutral-300 px-2 py-1"
                    />
                  </label>
                  <label className="col-span-1 flex items-center gap-2 text-neutral-600">
                    Duration (min)
                    <input
                      type="number"
                      min={5}
                      step={5}
                      value={formDuration}
                      onChange={(e) => setFormDuration(Number(e.target.value))}
                      className="w-20 rounded-lg border border-neutral-300 px-2 py-1"
                    />
                  </label>
                  {formError && <p className="col-span-2 text-xs text-red-600">{formError}</p>}
                  <button
                    disabled={formSubmitting}
                    className="col-span-2 rounded-lg bg-emerald-500 px-3 py-2 font-medium text-white hover:bg-emerald-400 disabled:opacity-50"
                  >
                    {formSubmitting ? "Submitting…" : "Submit to scheduler"}
                  </button>
                </form>
              )}

              {cardsByTab.length === 0 ? (
                <p className="text-sm text-neutral-500">Nothing here yet.</p>
              ) : (
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
                  {cardsByTab.map((card, i) => {
                    const theme = CARD_THEMES[i % CARD_THEMES.length];
                    const barPercent =
                      card.durationMinutes != null
                        ? Math.max(8, Math.min(100, (card.durationMinutes / DURATION_CAP_MINUTES) * 100))
                        : null;
                    return (
                      <div key={card.key} className={`flex flex-col rounded-xl ${theme.bg} p-4`}>
                        <span
                          className={`inline-block w-fit rounded-full px-2 py-0.5 text-xs font-medium ${
                            card.kind === "placed" ? theme.badge : badgeColor[card.kind]
                          }`}
                        >
                          {card.badgeText}
                        </span>
                        <p className="mt-2 font-semibold leading-snug">{card.title}</p>
                        <p className="mt-1 text-xs text-neutral-600">{card.subtitle}</p>

                        {barPercent != null ? (
                          <div className="mt-3 flex items-center gap-2">
                            <div className="h-1.5 flex-1 rounded-full bg-white/70">
                              <div className={`h-1.5 rounded-full ${theme.bar}`} style={{ width: `${barPercent}%` }} />
                            </div>
                            <span className="shrink-0 text-[11px] text-neutral-600">{card.durationMinutes}m</span>
                          </div>
                        ) : (
                          card.caption && <p className="mt-3 text-xs italic text-neutral-500">{card.caption}</p>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </section>

            <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
              {/* Weekly Progress */}
              <section className="rounded-2xl border border-neutral-200 bg-white p-5">
                <div className="mb-4 flex items-center justify-between">
                  <h2 className="text-base font-semibold">Weekly Progress</h2>
                  <Calendar size={16} className="text-neutral-400" />
                </div>
                <div className="mb-4">
                  <span className="text-3xl font-semibold">{board.placed.length}</span>
                  <span className="ml-2 text-sm text-neutral-500">Tasks Placed</span>
                </div>
                <div className="flex h-32 items-end gap-3">
                  {WEEKDAYS.map((day, i) => (
                    <div key={day} className="flex flex-1 flex-col items-center gap-1">
                      <div
                        className={`w-full rounded-t-md ${i === todayIndex ? "bg-orange-400" : "bg-neutral-200"}`}
                        style={{ height: `${(weekdayCounts[i] / maxWeekdayCount) * 100}%`, minHeight: weekdayCounts[i] > 0 ? "4px" : "0" }}
                      />
                      <span className="text-[11px] text-neutral-500">{day}</span>
                    </div>
                  ))}
                </div>
                <div className="mt-5 grid grid-cols-4 gap-2 border-t border-neutral-100 pt-4 text-center">
                  <div>
                    <div className="text-lg font-semibold">{board.placed.length}</div>
                    <div className="text-[11px] text-neutral-500">Placed</div>
                  </div>
                  <div>
                    <div className="text-lg font-semibold">{board.rejected.length}</div>
                    <div className="text-[11px] text-neutral-500">Rejected</div>
                  </div>
                  <div>
                    <div className="text-lg font-semibold">{board.unresolved.length}</div>
                    <div className="text-[11px] text-neutral-500">Unresolved</div>
                  </div>
                  <div>
                    <div className="text-lg font-semibold">{new Set(board.placed.map((t) => t.owner)).size}</div>
                    <div className="text-[11px] text-neutral-500">People active</div>
                  </div>
                </div>
              </section>

              {/* Next Up */}
              <section className="rounded-2xl border border-neutral-200 bg-white p-5">
                <h2 className="mb-4 text-base font-semibold">Next Up</h2>

                <div className="mb-4 flex justify-between border-b border-neutral-100 pb-4">
                  {weekDates.map((d, i) => {
                    const isToday = isSameDay(d, new Date());
                    return (
                      <div key={i} className="flex flex-col items-center gap-1.5">
                        <span className="text-[11px] text-neutral-400">{WEEKDAYS[i]}</span>
                        <span
                          className={`flex h-7 w-7 items-center justify-center rounded-full text-xs font-medium ${
                            isToday ? "bg-lime-300 text-lime-950" : "text-neutral-600"
                          }`}
                        >
                          {d.getDate()}
                        </span>
                      </div>
                    );
                  })}
                </div>

                {upcoming.length === 0 ? (
                  <p className="text-sm text-neutral-500">No upcoming tasks.</p>
                ) : (
                  <ul className="space-y-3">
                    {upcoming.map((task, i) => {
                      const isToday = isSameDay(new Date(task.start), new Date());
                      return (
                        <li key={`${task.owner}-${task.start}-${i}`} className="flex items-center gap-3 text-sm">
                          <div className="min-w-0 flex-1">
                            <p className="truncate font-medium">{task.description}</p>
                            <span className={`text-xs font-medium ${isToday ? "text-emerald-600" : "text-neutral-400"}`}>
                              {isToday ? "Today" : "Upcoming"}
                            </span>
                          </div>
                          <div className="flex shrink-0 items-center gap-2">
                            <div className="flex h-7 w-7 items-center justify-center rounded-full bg-violet-100 text-[11px] font-semibold text-violet-800">
                              {initials(personName(task.owner))}
                            </div>
                            <span className="hidden text-xs text-neutral-500 sm:inline">{personName(task.owner)}</span>
                          </div>
                          <div className="shrink-0 text-right">
                            <p className="text-xs font-medium">{formatTime(task.start)}</p>
                            <p className="text-[11px] text-neutral-400">
                              {Math.round((new Date(task.end).getTime() - new Date(task.start).getTime()) / 60_000)} min
                            </p>
                          </div>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </section>
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}
