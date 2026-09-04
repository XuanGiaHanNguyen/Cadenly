"use client";

import { useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";

const BACKEND_URL = "http://localhost:8080";
const DEMO_RESOURCE_ID = "11111111-1111-1111-1111-111111111111";

type BookedEvent = {
  eventId: string;
  resourceId: string;
  slot: { start: string; end: string };
  occurredAt: string;
};

export default function Home() {
  const [connected, setConnected] = useState(false);
  const [events, setEvents] = useState<BookedEvent[]>([]);
  const [lastError, setLastError] = useState<string | null>(null);
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(`${BACKEND_URL}/ws`),
      reconnectDelay: 2000,
      onConnect: () => {
        setConnected(true);
        client.subscribe("/topic/bookings", (message) => {
          const event: BookedEvent = JSON.parse(message.body);
          setEvents((prev) => [event, ...prev].slice(0, 50));
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
  }, []);

  async function simulateBooking() {
    setLastError(null);
    // Spread candidate start times across a wide window so most clicks succeed,
    // while occasionally colliding to demonstrate a rejected booking too.
    const offsetHours = Math.floor(Math.random() * 12);
    const start = new Date(Date.now() + offsetHours * 60 * 60_000);
    const end = new Date(start.getTime() + 60 * 60_000);

    const response = await fetch(
      `${BACKEND_URL}/api/resources/${DEMO_RESOURCE_ID}/bookings`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          start: start.toISOString(),
          end: end.toISOString(),
        }),
      },
    );

    if (!response.ok) {
      setLastError(
        "Booking rejected (conflict with an existing slot on this resource)",
      );
    }
  }

  return (
    <main className="min-h-screen bg-slate-950 p-8 text-slate-100">
      <div className="mx-auto max-w-2xl space-y-6">
        <div>
          <h1 className="text-2xl font-semibold">
            Smart Meeting Scheduler — Live Bookings
          </h1>
          <p className="mt-1 text-sm text-slate-400">
            Phase 4 proof: WebSocket (STOMP over SockJS) pushed from Service
            A on every successful booking.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <span
            className={`inline-block h-2.5 w-2.5 rounded-full ${connected ? "bg-emerald-400" : "bg-red-500"}`}
          />
          <span className="text-sm">
            {connected ? "Connected to /topic/bookings" : "Disconnected"}
          </span>
        </div>

        <button
          onClick={simulateBooking}
          className="rounded-md bg-emerald-500 px-4 py-2 font-medium text-slate-950 transition hover:bg-emerald-400"
        >
          Simulate booking on demo room
        </button>

        {lastError && <p className="text-sm text-red-400">{lastError}</p>}

        <div className="space-y-2">
          <h2 className="text-lg font-medium">Live feed ({events.length})</h2>
          {events.length === 0 && (
            <p className="text-sm text-slate-500">
              No bookings yet — click the button above.
            </p>
          )}
          <ul className="space-y-2">
            {events.map((event) => (
              <li
                key={event.eventId}
                className="rounded-md border border-slate-800 bg-slate-900 p-3 text-sm"
              >
                <div className="font-mono text-emerald-300">
                  {event.resourceId.slice(0, 8)}…
                </div>
                <div>
                  {new Date(event.slot.start).toLocaleString()} →{" "}
                  {new Date(event.slot.end).toLocaleTimeString()}
                </div>
                <div className="text-xs text-slate-500">
                  received {new Date(event.occurredAt).toLocaleTimeString()}
                </div>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </main>
  );
}
