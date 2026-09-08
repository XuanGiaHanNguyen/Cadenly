"use client";

import { useEffect, useState } from "react";
import { LogOut } from "lucide-react";
import { apiFetch } from "@/lib/api";
import { Sidebar } from "@/components/Sidebar";
import { TopBar } from "@/components/TopBar";
import { useAppShell } from "@/hooks/useAppShell";

const WORK_DAYS = [
  { code: "MON", label: "Mon" },
  { code: "TUE", label: "Tue" },
  { code: "WED", label: "Wed" },
  { code: "THU", label: "Thu" },
  { code: "FRI", label: "Fri" },
  { code: "SAT", label: "Sat" },
  { code: "SUN", label: "Sun" },
];

function initials(name: string): string {
  return name
    .split(" ")
    .map((part) => part[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();
}

export default function SettingsPage() {
  const { user, checkingAuth, connected, liveEvents, personName, simulateBooking, logout } = useAppShell();

  const [search, setSearch] = useState("");
  const [workDays, setWorkDays] = useState<Set<string>>(new Set());
  const [workStartTime, setWorkStartTime] = useState("09:00");
  const [workEndTime, setWorkEndTime] = useState("17:00");
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!user) return;
    setWorkDays(new Set(user.workDays ?? []));
    setWorkStartTime(user.workStartTime ?? "09:00");
    setWorkEndTime(user.workEndTime ?? "17:00");
  }, [user]);

  function toggleWorkDay(code: string) {
    setSaved(false);
    setWorkDays((prev) => {
      const next = new Set(prev);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return next;
    });
  }

  const canSave = workDays.size > 0 && workStartTime < workEndTime;

  async function saveSchedule() {
    if (!user) return;
    setSaving(true);
    setSaved(false);
    setError(null);

    try {
      const response = await apiFetch("/api/auth/onboarding", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          occupation: user.occupation,
          workDays: WORK_DAYS.map((d) => d.code).filter((code) => workDays.has(code)),
          workStartTime,
          workEndTime,
          calendarPreference: user.calendarPreference,
        }),
      });
      if (!response.ok) {
        setError("Something went wrong saving your schedule.");
        return;
      }
      setSaved(true);
    } catch {
      setError("Could not reach scheduler-engine on localhost:8080 - is it running?");
    } finally {
      setSaving(false);
    }
  }

  if (checkingAuth || !user) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-neutral-50 text-sm text-neutral-500">
        Checking session…
      </div>
    );
  }

  return (
    <div className="flex min-h-screen bg-neutral-50 text-neutral-900">
      <Sidebar active="settings" />

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
          <div className="mx-auto max-w-3xl space-y-5">
            <h1 className="text-2xl font-semibold">Settings</h1>

            {/* Profile */}
            <section className="rounded-2xl border border-neutral-200 bg-white p-5">
              <h2 className="mb-4 text-base font-semibold">Profile</h2>
              <div className="flex items-center gap-4">
                <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-brand-900 text-sm font-semibold text-white">
                  {initials(user.displayName)}
                </div>
                <div>
                  <p className="text-sm font-medium">{user.displayName}</p>
                  <p className="text-xs text-neutral-500">{user.email}</p>
                </div>
              </div>
              <div className="mt-4 grid grid-cols-2 gap-4 border-t border-neutral-100 pt-4 text-sm">
                <div>
                  <p className="text-xs text-neutral-400">Occupation</p>
                  <p className="mt-0.5 font-medium">{user.occupation ?? "—"}</p>
                </div>
                <div>
                  <p className="text-xs text-neutral-400">Calendar</p>
                  <p className="mt-0.5 font-medium">
                    {user.calendarPreference === "google" ? "Connected via Google Calendar" : "Built with Cadenly"}
                  </p>
                </div>
              </div>
            </section>

            {/* Work schedule */}
            <section className="rounded-2xl border border-neutral-200 bg-white p-5">
              <h2 className="text-base font-semibold">Work schedule</h2>
              <p className="mt-1 text-sm text-neutral-500">
                We keep scheduling inside these hours and gray out the rest on your calendar.
              </p>

              <div className="mt-4 flex flex-wrap gap-2">
                {WORK_DAYS.map((day) => (
                  <button
                    key={day.code}
                    type="button"
                    onClick={() => toggleWorkDay(day.code)}
                    className={`rounded-lg border px-3 py-2 text-sm font-medium transition ${
                      workDays.has(day.code)
                        ? "border-brand-900 bg-brand-900 text-white"
                        : "border-neutral-200 text-neutral-700 hover:border-neutral-400"
                    }`}
                  >
                    {day.label}
                  </button>
                ))}
              </div>

              <div className="mt-4 flex items-center gap-3">
                <label className="flex-1 text-sm text-neutral-600">
                  Start
                  <input
                    type="time"
                    value={workStartTime}
                    onChange={(e) => {
                      setWorkStartTime(e.target.value);
                      setSaved(false);
                    }}
                    className="mt-1 w-full rounded-lg border border-neutral-300 px-3 py-2 outline-none focus:border-neutral-500"
                  />
                </label>
                <label className="flex-1 text-sm text-neutral-600">
                  End
                  <input
                    type="time"
                    value={workEndTime}
                    onChange={(e) => {
                      setWorkEndTime(e.target.value);
                      setSaved(false);
                    }}
                    className="mt-1 w-full rounded-lg border border-neutral-300 px-3 py-2 outline-none focus:border-neutral-500"
                  />
                </label>
              </div>

              {workStartTime >= workEndTime && (
                <p className="mt-2 text-xs text-red-600">End time must be after start time.</p>
              )}
              {error && <p className="mt-2 text-xs text-red-600">{error}</p>}

              <div className="mt-4 flex items-center gap-3">
                <button
                  disabled={!canSave || saving}
                  onClick={saveSchedule}
                  className="rounded-lg bg-brand-900 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-800 disabled:opacity-40"
                >
                  {saving ? "Saving…" : "Save changes"}
                </button>
                {saved && <span className="text-xs font-medium text-emerald-600">Saved</span>}
              </div>
            </section>

            {/* Account */}
            <section className="rounded-2xl border border-neutral-200 bg-white p-5">
              <h2 className="mb-4 text-base font-semibold">Account</h2>
              <button
                onClick={logout}
                className="flex items-center gap-2 rounded-lg border border-neutral-200 px-4 py-2 text-sm font-medium text-neutral-700 hover:bg-neutral-50"
              >
                <LogOut size={14} /> Log out
              </button>
            </section>
          </div>
        </main>
      </div>
    </div>
  );
}
