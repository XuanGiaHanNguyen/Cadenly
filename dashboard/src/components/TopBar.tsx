"use client";

import { useEffect, useRef, useState } from "react";
import { Search, Bell, ChevronDown, LogOut } from "lucide-react";
import type { CurrentUser } from "@/lib/api";
import type { BookedEvent } from "@/hooks/useAppShell";

function initials(name: string): string {
  return name
    .split(" ")
    .map((part) => part[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" });
}

/** Search + live-bookings bell + user menu shown at the top of every authenticated page. */
export function TopBar({
  search,
  onSearchChange,
  searchPlaceholder = "Search",
  user,
  connected,
  liveEvents,
  personName,
  onSimulateBooking,
  onLogout,
}: {
  search: string;
  onSearchChange: (value: string) => void;
  searchPlaceholder?: string;
  user: CurrentUser | null;
  connected: boolean;
  liveEvents: BookedEvent[];
  personName: (id: string) => string;
  onSimulateBooking: () => void;
  onLogout: () => void;
}) {
  const [notifOpen, setNotifOpen] = useState(false);
  const [avatarOpen, setAvatarOpen] = useState(false);
  const notifRef = useRef<HTMLDivElement>(null);
  const avatarRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        searchRef.current?.focus();
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (notifRef.current && !notifRef.current.contains(e.target as Node)) setNotifOpen(false);
      if (avatarRef.current && !avatarRef.current.contains(e.target as Node)) setAvatarOpen(false);
    }
    document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, []);

  return (
    <div className="flex items-center justify-between border-b border-neutral-200 bg-white px-8 py-3">
      <label className="flex w-80 items-center gap-2 rounded-lg border border-neutral-200 bg-neutral-50 px-3 py-2 text-sm text-neutral-400">
        <Search size={16} />
        <input
          ref={searchRef}
          value={search}
          onChange={(e) => onSearchChange(e.target.value)}
          placeholder={searchPlaceholder}
          className="w-full bg-transparent text-neutral-900 outline-none placeholder:text-neutral-400"
        />
        <kbd className="rounded border border-neutral-200 bg-white px-1.5 py-0.5 text-[10px] text-neutral-400">⌘K</kbd>
      </label>

      <div className="flex items-center gap-4">
        <div className="relative" ref={notifRef}>
          <button
            onClick={() => setNotifOpen((v) => !v)}
            className="relative flex h-9 w-9 items-center justify-center rounded-full border border-neutral-200 text-neutral-500 hover:bg-neutral-50"
          >
            <Bell size={16} />
            {liveEvents.length > 0 && (
              <span className="absolute -right-1 -top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-rose-500 px-1 text-[10px] font-medium text-white">
                {liveEvents.length > 9 ? "9+" : liveEvents.length}
              </span>
            )}
          </button>

          {notifOpen && (
            <div className="absolute right-0 z-10 mt-2 w-80 rounded-xl border border-neutral-200 bg-white p-3 shadow-lg">
              <div className="mb-2 flex items-center justify-between">
                <div className="flex items-center gap-2 text-sm font-semibold">
                  <span className={`inline-block h-2 w-2 rounded-full ${connected ? "bg-emerald-500" : "bg-red-400"}`} />
                  Live bookings
                </div>
                <button
                  onClick={onSimulateBooking}
                  className="rounded-lg bg-neutral-100 px-2 py-1 text-[11px] font-medium hover:bg-neutral-200"
                >
                  Simulate booking
                </button>
              </div>
              {liveEvents.length === 0 ? (
                <p className="px-1 py-2 text-xs text-neutral-500">No events yet this session.</p>
              ) : (
                <ul className="max-h-72 space-y-1.5 overflow-y-auto">
                  {liveEvents.map((event) => (
                    <li key={event.eventId} className="flex items-center gap-2.5 rounded-lg border border-neutral-100 p-2 text-sm">
                      <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-violet-100 text-[11px] font-semibold text-violet-800">
                        {initials(personName(event.resourceId))}
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-xs font-medium">{personName(event.resourceId)}</p>
                        <p className="text-[11px] text-neutral-500">
                          {formatTime(event.slot.start)}–{formatTime(event.slot.end)}
                        </p>
                      </div>
                      <span className="shrink-0 text-[11px] text-neutral-400">{formatTime(event.occurredAt)}</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </div>

        <div className="relative" ref={avatarRef}>
          <button onClick={() => setAvatarOpen((v) => !v)} className="flex items-center gap-2">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-brand-900 text-xs font-semibold text-white">
              {user ? initials(user.displayName) : ""}
            </div>
            <span className="text-sm font-medium">{user?.displayName}</span>
            <ChevronDown size={14} className="text-neutral-400" />
          </button>

          {avatarOpen && (
            <div className="absolute right-0 z-10 mt-2 w-44 rounded-xl border border-neutral-200 bg-white p-1.5 shadow-lg">
              <button
                onClick={onLogout}
                className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-neutral-700 hover:bg-neutral-50"
              >
                <LogOut size={14} /> Log out
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
