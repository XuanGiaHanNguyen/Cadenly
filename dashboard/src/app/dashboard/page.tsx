"use client";

import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { apiFetch, BACKEND_URL, fetchCurrentUser, hasCompletedOnboarding, type CurrentUser } from "@/lib/api";
import { Logo } from "@/components/Logo";

const DEMO_RESOURCE_ID = "11111111-1111-1111-1111-111111111111";

type Owner = { id: string; name: string };
type PlacedTask = { description: string; owner: string; start: string; end: string };
type RejectedTask = { description: string; owner: string; reason: string };
type UnresolvedTask = { ownerNameRaw: string; description: string; reason: string };
type TaskBoard = { placed: PlacedTask[]; rejected: RejectedTask[]; unresolved: UnresolvedTask[] };
type BookedEvent = {
  eventId: string;
  resourceId: string;
  slot: { start: string; end: string };
  occurredAt: string;
};

const CARD_THEMES = [
  { bg: "bg-orange-100", badge: "bg-orange-200 text-orange-900" },
  { bg: "bg-violet-100", badge: "bg-violet-200 text-violet-900" },
  { bg: "bg-lime-100", badge: "bg-lime-200 text-lime-900" },
  { bg: "bg-sky-100", badge: "bg-sky-200 text-sky-900" },
];

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

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

export default function DashboardPage() {
  const router = useRouter();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [checkingAuth, setCheckingAuth] = useState(true);

  const [owners, setOwners] = useState<Owner[]>([]);
  const [board, setBoard] = useState<TaskBoard>({ placed: [], rejected: [], unresolved: [] });
  const [loadError, setLoadError] = useState<string | null>(null);

  const [connected, setConnected] = useState(false);
  const [liveEvents, setLiveEvents] = useState<BookedEvent[]>([]);
  const clientRef = useRef<Client | null>(null);

  const [formOpen, setFormOpen] = useState(false);
  const [formOwner, setFormOwner] = useState("");
  const [formDescription, setFormDescription] = useState("");
  const [formDeadline, setFormDeadline] = useState("");
  const [formPriority, setFormPriority] = useState(5);
  const [formDuration, setFormDuration] = useState(30);
  const [formSubmitting, setFormSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    fetchCurrentUser().then((currentUser) => {
      if (!currentUser) {
        router.replace("/login");
        return;
      }
      if (!hasCompletedOnboarding(currentUser)) {
        router.replace("/onboarding");
        return;
      }
      setUser(currentUser);
      setCheckingAuth(false);
    });
  }, [router]);

  async function loadOwners() {
    const response = await apiFetch("/api/owners");
    if (!response.ok) throw new Error("failed to load owners");
    setOwners(await response.json());
  }

  async function loadTasks() {
    const response = await apiFetch("/api/tasks");
    if (!response.ok) throw new Error("failed to load tasks");
    setBoard(await response.json());
  }

  useEffect(() => {
    if (checkingAuth) return;
    Promise.all([loadOwners(), loadTasks()]).catch(() =>
      setLoadError("Could not reach scheduler-engine on localhost:8080 - is it running?"),
    );
  }, [checkingAuth]);

  useEffect(() => {
    if (checkingAuth) return;
    const client = new Client({
      webSocketFactory: () => new SockJS(`${BACKEND_URL}/ws`),
      reconnectDelay: 2000,
      onConnect: () => {
        setConnected(true);
        client.subscribe("/topic/bookings", (message) => {
          const event: BookedEvent = JSON.parse(message.body);
          setLiveEvents((prev) => [event, ...prev].slice(0, 20));
        });
      },
      onDisconnect: () => setConnected(false),
      onWebSocketClose: () => setConnected(false),
    });
    client.activate();
    clientRef.current = client;
    return () => {
      clientRef.current?.deactivate();
    };
  }, [checkingAuth]);

  const ownerName = useMemo(() => {
    const byId = new Map(owners.map((o) => [o.id, o.name]));
    return (id: string) => byId.get(id) ?? `${id.slice(0, 8)}…`;
  }, [owners]);

  const weekdayCounts = useMemo(() => {
    const counts = new Array(7).fill(0);
    for (const task of board.placed) {
      counts[new Date(task.start).getDay()] += 1;
    }
    return counts;
  }, [board.placed]);

  const maxWeekdayCount = Math.max(1, ...weekdayCounts);

  const upcoming = useMemo(
    () =>
      [...board.placed].sort((a, b) => new Date(a.start).getTime() - new Date(b.start).getTime()).slice(0, 6),
    [board.placed],
  );

  async function simulateBooking() {
    const offsetHours = Math.floor(Math.random() * 12);
    const start = new Date(Date.now() + offsetHours * 60 * 60_000);
    const end = new Date(start.getTime() + 60 * 60_000);

    await apiFetch(`/api/resources/${DEMO_RESOURCE_ID}/bookings`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ start: start.toISOString(), end: end.toISOString() }),
    });
  }

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
              ownerName: formOwner,
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
        setFormError(`Owner not recognized: ${result.unresolved[0].reason}`);
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

  async function logout() {
    await apiFetch("/api/auth/logout", { method: "POST" });
    router.replace("/login");
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
      {/* Sidebar */}
      <aside className="flex w-60 shrink-0 flex-col border-r border-neutral-200 bg-white p-5">
        <div className="mb-8 px-1">
          <Logo />
        </div>
        <nav className="flex flex-col gap-1 text-sm">
          <span className="rounded-lg bg-lime-100 px-3 py-2 font-medium text-lime-900">Dashboard</span>
          <span className="cursor-not-allowed rounded-lg px-3 py-2 text-neutral-400">Owners (soon)</span>
          <span className="cursor-not-allowed rounded-lg px-3 py-2 text-neutral-400">Calendar (soon)</span>
          <span className="cursor-not-allowed rounded-lg px-3 py-2 text-neutral-400">Settings (soon)</span>
        </nav>
        <div className="mt-auto flex items-center gap-2 border-t border-neutral-100 pt-4">
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-brand-900 text-xs font-semibold text-white">
            {user ? initials(user.displayName) : ""}
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate text-xs font-medium">{user?.displayName}</p>
            <button onClick={logout} className="text-[11px] text-neutral-400 hover:text-neutral-600">
              Log out
            </button>
          </div>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 p-8">
        <div className="mx-auto max-w-6xl space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-2xl font-semibold">Dashboard</h1>
              <p className="text-sm text-neutral-500">Real data from a running scheduler-engine — nothing here is mocked.</p>
            </div>
            <div className="flex items-center gap-2">
              <span className={`inline-block h-2.5 w-2.5 rounded-full ${connected ? "bg-emerald-500" : "bg-red-400"}`} />
              <span className="text-xs text-neutral-500">{connected ? "Live" : "Disconnected"}</span>
            </div>
          </div>

          {loadError && (
            <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{loadError}</div>
          )}

          {/* Task cards */}
          <section className="rounded-2xl border border-neutral-200 bg-white p-5">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-base font-semibold">Placed Tasks</h2>
              <button
                onClick={() => setFormOpen((v) => !v)}
                className="rounded-lg bg-brand-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-brand-800"
              >
                {formOpen ? "Cancel" : "+ Add task"}
              </button>
            </div>

            {formOpen && (
              <form onSubmit={submitTask} className="mb-5 grid grid-cols-2 gap-3 rounded-xl bg-neutral-50 p-4 text-sm">
                <select
                  required
                  value={formOwner}
                  onChange={(e) => setFormOwner(e.target.value)}
                  className="col-span-1 rounded-lg border border-neutral-300 px-3 py-2"
                >
                  <option value="" disabled>
                    Owner
                  </option>
                  {owners.map((o) => (
                    <option key={o.id} value={o.name}>
                      {o.name}
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

            {upcoming.length === 0 ? (
              <p className="text-sm text-neutral-500">No tasks placed yet — add one above.</p>
            ) : (
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
                {upcoming.map((task, i) => {
                  const theme = CARD_THEMES[i % CARD_THEMES.length];
                  return (
                    <div key={`${task.owner}-${task.start}-${i}`} className={`rounded-xl ${theme.bg} p-4`}>
                      <span className={`inline-block rounded-full ${theme.badge} px-2 py-0.5 text-xs font-medium`}>
                        Due {formatDateShort(task.start)}
                      </span>
                      <p className="mt-2 font-semibold leading-snug">{task.description}</p>
                      <p className="mt-1 text-xs text-neutral-600">
                        {ownerName(task.owner)} · {formatTime(task.start)}–{formatTime(task.end)}
                      </p>
                    </div>
                  );
                })}
              </div>
            )}
          </section>

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            {/* Chart + stats */}
            <section className="rounded-2xl border border-neutral-200 bg-white p-5">
              <h2 className="mb-1 text-base font-semibold">Tasks by Day of Week</h2>
              <p className="mb-4 text-xs text-neutral-500">Across all placed tasks this server run</p>
              <div className="flex h-32 items-end gap-3">
                {WEEKDAYS.map((day, i) => (
                  <div key={day} className="flex flex-1 flex-col items-center gap-1">
                    <div
                      className="w-full rounded-t-md bg-orange-300"
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
                  <div className="text-[11px] text-neutral-500">Owners active</div>
                </div>
              </div>
            </section>

            {/* Live feed */}
            <section className="rounded-2xl border border-neutral-200 bg-white p-5">
              <div className="mb-3 flex items-center justify-between">
                <h2 className="text-base font-semibold">Live Feed</h2>
                <button
                  onClick={simulateBooking}
                  className="rounded-lg bg-neutral-100 px-3 py-1.5 text-xs font-medium hover:bg-neutral-200"
                >
                  Simulate booking
                </button>
              </div>
              <p className="mb-3 text-xs text-neutral-500">Pushed over WebSocket the instant a booking succeeds anywhere</p>
              {liveEvents.length === 0 ? (
                <p className="text-sm text-neutral-500">No events yet this session.</p>
              ) : (
                <ul className="space-y-2">
                  {liveEvents.map((event) => (
                    <li key={event.eventId} className="flex items-center gap-3 rounded-lg border border-neutral-100 p-2.5 text-sm">
                      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-violet-100 text-xs font-semibold text-violet-800">
                        {initials(ownerName(event.resourceId))}
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="truncate font-medium">{ownerName(event.resourceId)}</p>
                        <p className="text-xs text-neutral-500">
                          {formatTime(event.slot.start)}–{formatTime(event.slot.end)}
                        </p>
                      </div>
                      <span className="shrink-0 text-xs text-neutral-400">{formatTime(event.occurredAt)}</span>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        </div>
      </main>
    </div>
  );
}
