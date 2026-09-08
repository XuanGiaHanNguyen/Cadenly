"use client";

import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import Link from "next/link";
import { Home, ChevronRight, ChevronLeft, ChevronDown, Plus, X } from "lucide-react";
import { apiFetch } from "@/lib/api";
import { Sidebar } from "@/components/Sidebar";
import { TopBar } from "@/components/TopBar";
import { useAppShell } from "@/hooks/useAppShell";

type PlacedTask = { description: string; owner: string; start: string; end: string };
type TaskBoard = { placed: PlacedTask[]; rejected: unknown[]; unresolved: unknown[] };

type ViewMode = "month" | "week" | "day";
type LayoutMode = "calendar" | "list";
type CalEvent = { key: string; title: string; person: string; start: Date; end: Date };
type WorkSchedule = { days: Set<string>; startMinutes: number; endMinutes: number };

const WEEKDAY_LABELS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
const MONTH_NAMES = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December",
];

const EVENT_COLORS = [
  { bg: "bg-orange-100", text: "text-orange-900" },
  { bg: "bg-violet-100", text: "text-violet-900" },
  { bg: "bg-lime-100", text: "text-lime-900" },
  { bg: "bg-sky-100", text: "text-sky-900" },
  { bg: "bg-rose-100", text: "text-rose-900" },
  { bg: "bg-amber-100", text: "text-amber-900" },
  { bg: "bg-emerald-100", text: "text-emerald-900" },
  { bg: "bg-brand-100", text: "text-brand-900" },
];

const DAY_MINUTES = 24 * 60;
const HOUR_ROW_PX = 56;
const VISIBLE_HOURS = 10;
const DEFAULT_SCROLL_HOUR = 7;
const DAY_CODES = ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"];

const LAYOUT_TABS: { key: LayoutMode; label: string }[] = [
  { key: "calendar", label: "Calendar view" },
  { key: "list", label: "List view" },
];

const VIEW_LABELS: Record<ViewMode, string> = { month: "Month view", week: "Week view", day: "Day view" };

function colorForTitle(title: string) {
  let hash = 0;
  for (let i = 0; i < title.length; i++) hash = (hash * 31 + title.charCodeAt(i)) >>> 0;
  return EVENT_COLORS[hash % EVENT_COLORS.length];
}

function isSameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

function startOfWeek(date: Date): Date {
  const d = new Date(date);
  const mondayOffset = (d.getDay() + 6) % 7;
  d.setDate(d.getDate() - mondayOffset);
  d.setHours(0, 0, 0, 0);
  return d;
}

function addDays(date: Date, n: number): Date {
  const d = new Date(date);
  d.setDate(d.getDate() + n);
  return d;
}

function formatTime(date: Date): string {
  return date.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" });
}

function formatDateShort(date: Date): string {
  return date.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

function dayCodeOf(date: Date): string {
  return DAY_CODES[date.getDay()];
}

function parseTimeToMinutes(time: string): number {
  const [h, m] = time.split(":").map(Number);
  return h * 60 + m;
}

/** Percent-of-day top/height for the grayed-out (non-working) bands in a day column. */
function nonWorkingSegments(date: Date, schedule: WorkSchedule | null): { top: number; height: number }[] {
  if (!schedule) return [];
  if (!schedule.days.has(dayCodeOf(date))) return [{ top: 0, height: 100 }];
  const segments: { top: number; height: number }[] = [];
  if (schedule.startMinutes > 0) segments.push({ top: 0, height: (schedule.startMinutes / DAY_MINUTES) * 100 });
  if (schedule.endMinutes < DAY_MINUTES) {
    segments.push({ top: (schedule.endMinutes / DAY_MINUTES) * 100, height: 100 - (schedule.endMinutes / DAY_MINUTES) * 100 });
  }
  return segments;
}

/** Greedy interval-graph column assignment so overlapping events sit side by side. */
function layoutDayEvents(events: CalEvent[]): { event: CalEvent; col: number; cols: number }[] {
  const sorted = [...events].sort((a, b) => a.start.getTime() - b.start.getTime());
  const colEnds: number[] = [];
  const placed = sorted.map((event) => {
    let col = colEnds.findIndex((end) => end <= event.start.getTime());
    if (col === -1) {
      col = colEnds.length;
      colEnds.push(event.end.getTime());
    } else {
      colEnds[col] = event.end.getTime();
    }
    return { event, col };
  });
  const cols = Math.max(1, colEnds.length);
  return placed.map((p) => ({ ...p, cols }));
}

export default function CalendarPage() {
  const { user, checkingAuth, people, peopleError, personName, connected, liveEvents, simulateBooking, logout } =
    useAppShell();

  const [board, setBoard] = useState<TaskBoard>({ placed: [], rejected: [], unresolved: [] });
  const [loadError, setLoadError] = useState<string | null>(null);

  const [layoutMode, setLayoutMode] = useState<LayoutMode>("calendar");
  const [view, setView] = useState<ViewMode>("month");
  const [viewMenuOpen, setViewMenuOpen] = useState(false);
  const viewMenuRef = useRef<HTMLDivElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  const [refDate, setRefDate] = useState(() => new Date());
  const [search, setSearch] = useState("");
  const [expandedDays, setExpandedDays] = useState<Set<string>>(new Set());

  const [addEventOpen, setAddEventOpen] = useState(false);
  const [formPerson, setFormPerson] = useState("");
  const [formDescription, setFormDescription] = useState("");
  const [formDeadline, setFormDeadline] = useState("");
  const [formPriority, setFormPriority] = useState(5);
  const [formDuration, setFormDuration] = useState(30);
  const [formSubmitting, setFormSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  async function loadTasks() {
    const response = await apiFetch("/api/tasks");
    if (!response.ok) throw new Error("failed to load tasks");
    setBoard(await response.json());
  }

  useEffect(() => {
    if (checkingAuth) return;
    loadTasks().catch(() => setLoadError("Could not reach scheduler-engine on localhost:8080 - is it running?"));
  }, [checkingAuth]);

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (viewMenuRef.current && !viewMenuRef.current.contains(e.target as Node)) setViewMenuOpen(false);
    }
    document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, []);

  const events = useMemo<CalEvent[]>(() => {
    const q = search.trim().toLowerCase();
    return board.placed
      .map((t, i) => ({
        key: `${t.owner}-${t.start}-${i}`,
        title: t.description,
        person: personName(t.owner),
        start: new Date(t.start),
        end: new Date(t.end),
      }))
      .filter((e) => !q || e.title.toLowerCase().includes(q) || e.person.toLowerCase().includes(q));
  }, [board.placed, personName, search]);

  const listGroups = useMemo(() => {
    const sorted = [...events].sort((a, b) => a.start.getTime() - b.start.getTime());
    const groups: { label: string; events: CalEvent[] }[] = [];
    for (const ev of sorted) {
      const label = ev.start.toLocaleDateString(undefined, { weekday: "long", month: "long", day: "numeric" });
      const last = groups[groups.length - 1];
      if (last && last.label === label) last.events.push(ev);
      else groups.push({ label, events: [ev] });
    }
    return groups;
  }, [events]);

  const workSchedule = useMemo<WorkSchedule | null>(() => {
    if (!user?.workDays || user.workDays.length === 0 || !user.workStartTime || !user.workEndTime) return null;
    return {
      days: new Set(user.workDays),
      startMinutes: parseTimeToMinutes(user.workStartTime),
      endMinutes: parseTimeToMinutes(user.workEndTime),
    };
  }, [user]);

  const today = new Date();

  // ---- Month view data ----
  const monthWeeks = useMemo(() => {
    const year = refDate.getFullYear();
    const month = refDate.getMonth();
    const firstOfMonth = new Date(year, month, 1);
    const lastOfMonth = new Date(year, month + 1, 0);
    const gridStart = startOfWeek(firstOfMonth);
    const gridEnd = addDays(startOfWeek(lastOfMonth), 6);

    const days: { date: Date; inMonth: boolean; isToday: boolean; events: CalEvent[] }[] = [];
    for (let d = new Date(gridStart); d <= gridEnd; d = addDays(d, 1)) {
      const dayEvents = events
        .filter((e) => isSameDay(e.start, d))
        .sort((a, b) => a.start.getTime() - b.start.getTime());
      days.push({ date: new Date(d), inMonth: d.getMonth() === month, isToday: isSameDay(d, today), events: dayEvents });
    }
    const weeks: typeof days[] = [];
    for (let i = 0; i < days.length; i += 7) weeks.push(days.slice(i, i + 7));
    return weeks;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [refDate, events]);

  // ---- Week/day view data (both render the same scrollable 24h grid) ----
  const weekDates = useMemo(() => {
    const start = startOfWeek(refDate);
    return Array.from({ length: 7 }, (_, i) => addDays(start, i));
  }, [refDate]);

  const columnDates = useMemo(() => (view === "day" ? [refDate] : weekDates), [view, refDate, weekDates]);

  const gridColumns = useMemo(() => {
    return columnDates.map((day) => {
      const dayEvents = events.filter((e) => isSameDay(e.start, day));
      return layoutDayEvents(dayEvents).map(({ event, col, cols }) => {
        const startMinutes = event.start.getHours() * 60 + event.start.getMinutes();
        const endMinutes = Math.max(startMinutes + 15, event.end.getHours() * 60 + event.end.getMinutes());
        return {
          event,
          col,
          cols,
          topPct: (startMinutes / DAY_MINUTES) * 100,
          heightPct: Math.max(2, ((endMinutes - startMinutes) / DAY_MINUTES) * 100),
        };
      });
    });
  }, [columnDates, events]);

  const hourMarks = useMemo(() => Array.from({ length: 24 }, (_, i) => i), []);

  // Scrolled full-day grid was cropping events outside a fixed 7am-9pm window;
  // now the whole day renders and we just scroll the viewport to business hours.
  useEffect(() => {
    if (!scrollRef.current) return;
    const startHour = workSchedule ? Math.floor(workSchedule.startMinutes / 60) : DEFAULT_SCROLL_HOUR;
    scrollRef.current.scrollTop = Math.max(0, startHour * HOUR_ROW_PX - HOUR_ROW_PX);
  }, [view, workSchedule]);

  function toggleExpanded(key: string) {
    setExpandedDays((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  function goToday() {
    setRefDate(new Date());
  }

  function goPrev() {
    setRefDate((d) =>
      view === "month" ? new Date(d.getFullYear(), d.getMonth() - 1, 1) : addDays(d, view === "day" ? -1 : -7),
    );
  }

  function goNext() {
    setRefDate((d) =>
      view === "month" ? new Date(d.getFullYear(), d.getMonth() + 1, 1) : addDays(d, view === "day" ? 1 : 7),
    );
  }

  async function submitEvent(e: FormEvent) {
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
      const result: { placed: unknown[]; rejected: { reason: string }[]; unresolved: { reason: string }[] } =
        await response.json();

      if (result.unresolved.length > 0) {
        setFormError(`Person not recognized: ${result.unresolved[0].reason}`);
      } else if (result.rejected.length > 0) {
        setFormError(`Rejected: ${result.rejected[0].reason}`);
      } else {
        setAddEventOpen(false);
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

  const monthLabel = `${MONTH_NAMES[refDate.getMonth()]} ${refDate.getFullYear()}`;
  const monthDaysCount = new Date(refDate.getFullYear(), refDate.getMonth() + 1, 0).getDate();
  const monthRange = `${MONTH_NAMES[refDate.getMonth()].slice(0, 3)} 1, ${refDate.getFullYear()} - ${MONTH_NAMES[refDate.getMonth()].slice(0, 3)} ${monthDaysCount}, ${refDate.getFullYear()}`;
  const weekStart = weekDates[0];
  const weekEnd = weekDates[6];
  const weekLabel = `Week of ${formatDateShort(weekStart)}`;
  const weekRange = `${formatDateShort(weekStart)} - ${formatDateShort(weekEnd)}, ${weekEnd.getFullYear()}`;
  const dayLabel = refDate.toLocaleDateString(undefined, { weekday: "long" });
  const dayRange = refDate.toLocaleDateString(undefined, { month: "long", day: "numeric", year: "numeric" });
  const headerLabel = view === "month" ? monthLabel : view === "week" ? weekLabel : dayLabel;
  const headerRange = view === "month" ? monthRange : view === "week" ? weekRange : dayRange;

  return (
    <div className="flex min-h-screen bg-neutral-50 text-neutral-900">
      <Sidebar active="calendar" />

      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar
          search={search}
          onSearchChange={setSearch}
          searchPlaceholder="Search events"
          user={user}
          connected={connected}
          liveEvents={liveEvents}
          personName={personName}
          onSimulateBooking={simulateBooking}
          onLogout={logout}
        />

        <main className="flex-1 p-8">
          <div className="mx-auto max-w-6xl space-y-4">
            {/* Breadcrumb */}
            <div className="flex items-center gap-1.5 text-sm text-neutral-400">
              <Link href="/dashboard" className="flex h-6 w-6 items-center justify-center rounded hover:bg-neutral-100 hover:text-neutral-600">
                <Home size={14} />
              </Link>
              <ChevronRight size={14} />
              <span>Cadenly</span>
              <ChevronRight size={14} />
              <span className="rounded-md border border-neutral-200 px-2 py-0.5 font-medium text-neutral-900">Schedule</span>
            </div>

            <div className="flex items-center justify-between gap-4">
              <h1 className="text-2xl font-semibold">Schedule</h1>

              {/* Calendar / List toggle */}
              <div className="inline-flex items-center gap-1 rounded-lg bg-neutral-100 p-1 text-sm">
                {LAYOUT_TABS.map((tab) => (
                  <button
                    key={tab.key}
                    onClick={() => setLayoutMode(tab.key)}
                    className={`rounded-md px-3 py-1.5 font-medium ${
                      layoutMode === tab.key ? "bg-white text-neutral-900 shadow-sm" : "text-neutral-500 hover:text-neutral-700"
                    }`}
                  >
                    {tab.label}
                  </button>
                ))}
              </div>
            </div>

            {(loadError || peopleError) && (
              <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                {loadError ?? peopleError}
              </div>
            )}

            {/* Calendar card */}
            <section className="rounded-2xl border border-neutral-200 bg-white p-5">
              <div className="mb-5 flex flex-wrap items-center justify-between gap-4">
                {layoutMode === "calendar" ? (
                  <div className="flex items-center gap-3">
                    <div className="flex w-14 shrink-0 flex-col items-center overflow-hidden rounded-lg border border-neutral-200 text-center">
                      <span className="w-full bg-neutral-50 py-0.5 text-[10px] font-semibold uppercase text-neutral-400">
                        {today.toLocaleDateString(undefined, { month: "short" })}
                      </span>
                      <span className="py-1 text-lg font-bold">{today.getDate()}</span>
                    </div>
                    <div>
                      <p className="text-base font-semibold">{headerLabel}</p>
                      <p className="text-xs text-neutral-500">{headerRange}</p>
                    </div>
                  </div>
                ) : (
                  <div>
                    <p className="text-base font-semibold">All events</p>
                    <p className="text-xs text-neutral-500">
                      {events.length} event{events.length === 1 ? "" : "s"}
                    </p>
                  </div>
                )}

                <div className="flex flex-wrap items-center gap-2">
                  {layoutMode === "calendar" && (
                    <>
                      <div className="flex items-center rounded-lg border border-neutral-200">
                        <button onClick={goPrev} className="flex h-8 w-8 items-center justify-center text-neutral-500 hover:bg-neutral-50">
                          <ChevronLeft size={16} />
                        </button>
                        <button
                          onClick={goToday}
                          className="border-x border-neutral-200 px-3 py-1.5 text-sm font-medium hover:bg-neutral-50"
                        >
                          Today
                        </button>
                        <button onClick={goNext} className="flex h-8 w-8 items-center justify-center text-neutral-500 hover:bg-neutral-50">
                          <ChevronRight size={16} />
                        </button>
                      </div>

                      <div className="relative" ref={viewMenuRef}>
                        <button
                          onClick={() => setViewMenuOpen((v) => !v)}
                          className="flex items-center gap-1.5 rounded-lg border border-neutral-200 px-3 py-1.5 text-sm font-medium hover:bg-neutral-50"
                        >
                          {VIEW_LABELS[view]} <ChevronDown size={14} className="text-neutral-400" />
                        </button>
                        {viewMenuOpen && (
                          <div className="absolute right-0 z-10 mt-1 w-36 rounded-xl border border-neutral-200 bg-white p-1 shadow-lg">
                            {(["month", "week", "day"] as ViewMode[]).map((v) => (
                              <button
                                key={v}
                                onClick={() => {
                                  setView(v);
                                  setViewMenuOpen(false);
                                }}
                                className={`block w-full rounded-lg px-3 py-1.5 text-left text-sm ${
                                  view === v ? "bg-neutral-100 font-medium text-neutral-900" : "text-neutral-600 hover:bg-neutral-50"
                                }`}
                              >
                                {VIEW_LABELS[v]}
                              </button>
                            ))}
                          </div>
                        )}
                      </div>
                    </>
                  )}

                  <button
                    onClick={() => setAddEventOpen(true)}
                    className="flex items-center gap-1 rounded-lg bg-brand-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-800"
                  >
                    <Plus size={14} /> Add event
                  </button>
                </div>
              </div>

              {layoutMode === "list" ? (
                <div className="divide-y divide-neutral-100">
                  {listGroups.length === 0 ? (
                    <p className="py-8 text-center text-sm text-neutral-400">No events found.</p>
                  ) : (
                    listGroups.map((group) => (
                      <div key={group.label} className="py-3 first:pt-0">
                        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-neutral-400">{group.label}</p>
                        <div className="space-y-1">
                          {group.events.map((ev) => {
                            const color = colorForTitle(ev.title);
                            return (
                              <div key={ev.key} className="flex items-center gap-3 rounded-lg px-2 py-2 hover:bg-neutral-50">
                                <span className={`h-2 w-2 shrink-0 rounded-full ${color.bg}`} />
                                <span className="w-32 shrink-0 text-xs text-neutral-500">
                                  {formatTime(ev.start)}–{formatTime(ev.end)}
                                </span>
                                <span className="min-w-0 flex-1 truncate text-sm font-medium">{ev.title}</span>
                                <span className="shrink-0 text-xs text-neutral-500">{ev.person}</span>
                              </div>
                            );
                          })}
                        </div>
                      </div>
                    ))
                  )}
                </div>
              ) : view === "month" ? (
                <div className="overflow-x-auto">
                  <div className="grid min-w-[860px] grid-cols-7 rounded-lg border border-neutral-200">
                    {WEEKDAY_LABELS.map((label) => (
                      <div
                        key={label}
                        className="border-b border-r border-neutral-200 bg-neutral-50 py-2 text-center text-xs font-medium text-neutral-500 last:border-r-0"
                      >
                        {label}
                      </div>
                    ))}
                    {monthWeeks.map((week) =>
                      week.map((day) => {
                        const iso = day.date.toDateString();
                        const expanded = expandedDays.has(iso);
                        const visible = expanded ? day.events : day.events.slice(0, 3);
                        const hiddenCount = day.events.length - visible.length;
                        const isNonWorkingDay = workSchedule != null && !workSchedule.days.has(dayCodeOf(day.date));
                        return (
                          <div
                            key={iso}
                            className={`min-h-[112px] border-b border-r border-neutral-200 p-2 last:border-r-0 ${
                              !day.inMonth ? "bg-neutral-50/70" : isNonWorkingDay ? "bg-neutral-100/60" : ""
                            }`}
                          >
                            <div className="mb-1.5 flex items-center justify-between">
                              <span
                                className={`flex h-6 w-6 items-center justify-center rounded-full text-[13px] font-medium ${
                                  day.isToday
                                    ? "bg-neutral-900 text-white"
                                    : day.inMonth
                                      ? "text-neutral-900"
                                      : "text-neutral-300"
                                }`}
                              >
                                {day.date.getDate()}
                              </span>
                            </div>
                            <div className="space-y-1">
                              {visible.map((ev) => {
                                const color = colorForTitle(ev.title);
                                return (
                                  <div
                                    key={ev.key}
                                    className={`flex items-center justify-between gap-1 truncate rounded-md px-1.5 py-0.5 text-[11px] font-medium ${color.bg} ${color.text}`}
                                    title={`${ev.title} · ${formatTime(ev.start)}–${formatTime(ev.end)}`}
                                  >
                                    <span className="truncate">{ev.title}</span>
                                    <span className="shrink-0 opacity-70">{formatTime(ev.start)}</span>
                                  </div>
                                );
                              })}
                              {hiddenCount > 0 && (
                                <button
                                  onClick={() => toggleExpanded(iso)}
                                  className="text-[11px] font-medium text-neutral-400 hover:text-neutral-600"
                                >
                                  {expanded ? "Show less" : `${hiddenCount} more…`}
                                </button>
                              )}
                            </div>
                          </div>
                        );
                      }),
                    )}
                  </div>
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <div
                    className={`overflow-hidden rounded-lg border border-neutral-200 ${view === "week" ? "min-w-[860px]" : ""}`}
                  >
                    <div className="grid" style={{ gridTemplateColumns: `56px repeat(${columnDates.length}, 1fr)` }}>
                      <div className="border-b border-r border-neutral-200 bg-neutral-50" />
                      {columnDates.map((d, i) => (
                        <div
                          key={i}
                          className="flex flex-col items-center gap-1 border-b border-r border-neutral-200 bg-neutral-50 py-2 last:border-r-0"
                        >
                          <span className="text-[11px] text-neutral-400">
                            {view === "day" ? d.toLocaleDateString(undefined, { weekday: "short" }) : WEEKDAY_LABELS[i]}
                          </span>
                          <span
                            className={`flex h-6 w-6 items-center justify-center rounded-full text-[13px] font-medium ${
                              isSameDay(d, today) ? "bg-neutral-900 text-white" : "text-neutral-900"
                            }`}
                          >
                            {d.getDate()}
                          </span>
                        </div>
                      ))}
                    </div>

                    <div ref={scrollRef} className="overflow-y-auto" style={{ maxHeight: VISIBLE_HOURS * HOUR_ROW_PX }}>
                      <div className="grid" style={{ gridTemplateColumns: `56px repeat(${columnDates.length}, 1fr)` }}>
                        <div style={{ height: hourMarks.length * HOUR_ROW_PX }} className="relative border-r border-neutral-200">
                          {hourMarks.map((h, i) => (
                            <span
                              key={h}
                              style={{ top: i * HOUR_ROW_PX - 7 }}
                              className="absolute right-2 text-[11px] text-neutral-400"
                            >
                              {h % 12 === 0 ? 12 : h % 12}
                              {h < 12 ? "am" : "pm"}
                            </span>
                          ))}
                        </div>

                        {gridColumns.map((dayEvents, dayIdx) => (
                          <div
                            key={dayIdx}
                            style={{ height: hourMarks.length * HOUR_ROW_PX }}
                            className="relative border-r border-neutral-200 last:border-r-0"
                          >
                            {nonWorkingSegments(columnDates[dayIdx], workSchedule).map((seg, i) => (
                              <div
                                key={i}
                                style={{ top: `${seg.top}%`, height: `${seg.height}%` }}
                                className="pointer-events-none absolute inset-x-0 bg-neutral-100/70"
                              />
                            ))}
                            {hourMarks.map((h, i) => (
                              <div key={h} style={{ top: i * HOUR_ROW_PX }} className="absolute left-0 right-0 border-t border-neutral-100" />
                            ))}
                            {dayEvents.map(({ event, col, cols, topPct, heightPct }) => {
                              const color = colorForTitle(event.title);
                              const width = 100 / cols;
                              return (
                                <div
                                  key={event.key}
                                  style={{
                                    top: `${topPct}%`,
                                    height: `${heightPct}%`,
                                    left: `${col * width}%`,
                                    width: `calc(${width}% - 4px)`,
                                  }}
                                  className={`absolute overflow-hidden rounded-md px-1.5 py-1 text-[11px] font-medium ${color.bg} ${color.text}`}
                                  title={`${event.title} · ${formatTime(event.start)}–${formatTime(event.end)}`}
                                >
                                  <p className="truncate font-semibold leading-tight">{event.title}</p>
                                  <p className="truncate leading-tight opacity-70">{formatTime(event.start)}–{formatTime(event.end)}</p>
                                </div>
                              );
                            })}
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </section>
          </div>
        </main>
      </div>

      {addEventOpen && (
        <div
          className="fixed inset-0 z-20 flex items-center justify-center bg-black/30 p-4"
          onClick={() => setAddEventOpen(false)}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            className="w-full max-w-md rounded-2xl border border-neutral-200 bg-white p-5 shadow-xl"
          >
            <div className="mb-4 flex items-center justify-between">
              <h3 className="text-base font-semibold">Add event</h3>
              <button
                onClick={() => setAddEventOpen(false)}
                className="rounded-lg p-1 text-neutral-400 hover:bg-neutral-100"
              >
                <X size={16} />
              </button>
            </div>
            <form onSubmit={submitEvent} className="grid grid-cols-2 gap-3 text-sm">
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
                className="col-span-2 rounded-lg bg-brand-900 px-3 py-2 font-medium text-white hover:bg-brand-800 disabled:opacity-50"
              >
                {formSubmitting ? "Adding…" : "Add to calendar"}
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
