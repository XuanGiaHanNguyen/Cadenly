"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { apiFetch, BACKEND_URL, fetchCurrentUser, hasCompletedOnboarding, type CurrentUser } from "@/lib/api";

const DEMO_RESOURCE_ID = "11111111-1111-1111-1111-111111111111";

/** A schedulable person - someone tasks/events can be assigned to. Backed by the `/api/owners` directory. */
export type Person = { id: string; name: string };
export type BookedEvent = {
  eventId: string;
  resourceId: string;
  slot: { start: string; end: string };
  occurredAt: string;
};

/**
 * Shared shell state for every authenticated page: the logged-in user
 * (redirecting to /login or /onboarding as needed), the assignable-people
 * directory, and the live-bookings websocket feed the TopBar's notification
 * bell shows.
 */
export function useAppShell() {
  const router = useRouter();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [checkingAuth, setCheckingAuth] = useState(true);

  const [people, setPeople] = useState<Person[]>([]);
  const [peopleError, setPeopleError] = useState<string | null>(null);

  const [connected, setConnected] = useState(false);
  const [liveEvents, setLiveEvents] = useState<BookedEvent[]>([]);
  const clientRef = useRef<Client | null>(null);

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

  useEffect(() => {
    if (checkingAuth) return;
    let cancelled = false;
    apiFetch("/api/owners")
      .then((response) => {
        if (!response.ok) throw new Error("failed to load people");
        return response.json();
      })
      .then((data) => {
        if (!cancelled) setPeople(data);
      })
      .catch(() => {
        if (!cancelled) setPeopleError("Could not reach scheduler-engine on localhost:8080 - is it running?");
      });
    return () => {
      cancelled = true;
    };
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

  function personName(id: string): string {
    const found = people.find((p) => p.id === id);
    return found ? found.name : `${id.slice(0, 8)}…`;
  }

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

  async function logout() {
    await apiFetch("/api/auth/logout", { method: "POST" });
    router.replace("/login");
  }

  return {
    user,
    checkingAuth,
    people,
    peopleError,
    personName,
    connected,
    liveEvents,
    simulateBooking,
    logout,
  };
}
