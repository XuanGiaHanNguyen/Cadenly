"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Mic, FileText, Brain, ListChecks, CalendarClock, ShieldCheck, ArrowRight, LayoutGrid } from "lucide-react";
import { fetchCurrentUser, hasCompletedOnboarding } from "@/lib/api";
import { Logo } from "@/components/Logo";
import { FloatingAvatars } from "@/components/FloatingAvatars";

const PIPELINE_STEPS = [
  { icon: Mic, label: "Audio recording", detail: "Meeting captured, nothing leaves the machine", theme: "bg-orange-100", iconColor: "text-orange-900" },
  { icon: FileText, label: "Transcription", detail: "Local Whisper, no cloud API", theme: "bg-violet-100", iconColor: "text-violet-900" },
  { icon: Brain, label: "Summarization", detail: "Hand-built TextRank over TF-IDF vectors", theme: "bg-lime-100", iconColor: "text-lime-900" },
  { icon: ListChecks, label: "Task extraction", detail: "Local Llama 3.1 8B, JSON-prompted", theme: "bg-sky-100", iconColor: "text-sky-900" },
  { icon: CalendarClock, label: "Scheduling", detail: "Weighted interval scheduling, DP-optimal", theme: "bg-orange-100", iconColor: "text-orange-900" },
  { icon: ShieldCheck, label: "Concurrency-safe commit", detail: "No double-booking, proven under load", theme: "bg-violet-100", iconColor: "text-violet-900" },
];

// Rendered twice back-to-back so the auto-scroll loop (see LandingPage) can
// reset seamlessly instead of jump-cutting back to the first card.
const CAROUSEL_STEPS = [...PIPELINE_STEPS, ...PIPELINE_STEPS];

// target is the part that counts up (or down, for the zero case); prefix/suffix stay static.
const PROOF_STATS = [
  { target: 120, prefix: "", suffix: " vs 100", label: "Higher-value schedules than simple first-come, first-served" },
  { target: 1, prefix: "", suffix: " of 10", label: "Zero double-bookings — even when everyone books at once" },
  { target: 34, prefix: "", suffix: "ms", label: "Faster than a page refresh, every single time" },
  { target: 0, prefix: "", suffix: "", label: "Nothing about your calendar ever leaves your machine" },
];

// Previews below are static illustrations of the real dashboard components
// (see /dashboard) - the same weekday chart, live feed, and outcome states,
// not a mockup of a different, imagined product.
const WEEKDAY_PREVIEW = [
  { day: "Sun", pct: 15 },
  { day: "Mon", pct: 55 },
  { day: "Tue", pct: 90 },
  { day: "Wed", pct: 40 },
  { day: "Thu", pct: 70 },
  { day: "Fri", pct: 30 },
  { day: "Sat", pct: 10 },
];

const PREVIEW_AVATARS = [
  { initials: "SK", theme: "bg-violet-100 text-violet-900" },
  { initials: "JD", theme: "bg-orange-100 text-orange-900" },
  { initials: "PR", theme: "bg-lime-100 text-lime-900" },
];

const LIVE_FEED_PREVIEW = [
  { initials: "SK", theme: "bg-violet-100 text-violet-900", name: "Sarah Kim", time: "2:00–2:30 PM", ago: "just now" },
  { initials: "JD", theme: "bg-orange-100 text-orange-900", name: "John", time: "10:00–10:30 AM", ago: "1m ago" },
];

const OUTCOME_PREVIEW = [
  { label: "Placed", count: 14, theme: "bg-emerald-100 text-emerald-800" },
  { label: "Rejected", count: 2, theme: "bg-red-100 text-red-800" },
  { label: "Unresolved", count: 1, theme: "bg-amber-100 text-amber-800" },
];

// Each avatar drifts within its own small area: a different amplitude, speed,
// and start offset per one so the group wanders out of sync instead of
// bobbing together like a single unit.
const AVATARS = [
  { initials: "SK", theme: "bg-violet-100 text-violet-900", position: "left-[4%] top-6 sm:left-[8%] sm:top-10", driftX: 14, driftY: 10, duration: 8, delay: 0 },
  { initials: "JD", theme: "bg-orange-100 text-orange-900", position: "right-[4%] top-2 sm:right-[9%] sm:top-6", driftX: 10, driftY: 14, duration: 10, delay: 1.2 },
  { initials: "PR", theme: "bg-lime-100 text-lime-900", position: "left-[-1%] top-[21rem] sm:left-[6%] sm:bottom-6", driftX: 16, driftY: 8, duration: 9, delay: 2.4 },
  { initials: "AM", theme: "bg-sky-100 text-sky-900", position: "right-[1%] top-[25rem] sm:right-[7%] sm:bottom-12", driftX: 9, driftY: 12, duration: 11, delay: 0.6 },
];

/**
 * Counts from a starting value up (or, for a target of 0, down from 4) to
 * the target once the tile scrolls into view - a one-shot odometer flash,
 * not a repeating animation. Only the numeric target animates; any
 * surrounding prefix/suffix text (" vs 100", "ms", ...) stays static.
 */
function ProofStat({
  target,
  prefix,
  suffix,
  label,
}: {
  target: number;
  prefix: string;
  suffix: string;
  label: string;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const [active, setActive] = useState(false);
  const [display, setDisplay] = useState(target === 0 ? 4 : 0);
  // Lazy initializer, not an effect: read once during this component's first
  // render, so there's no setState-in-effect (nor a ref read during render)
  // needed to know it later. Safe from hydration mismatch too - the branch
  // that reads it below only matters once `active` is true, which is never
  // the case on the very first (SSR or pre-hydration) render.
  const [prefersReducedMotion] = useState(
    () => typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches,
  );

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setActive(true);
          observer.disconnect();
        }
      },
      { threshold: 0.4 },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (!active || prefersReducedMotion) return;
    const from = target === 0 ? 4 : 0;
    const durationMs = 900;
    let start: number | null = null;
    let raf = 0;

    function step(ts: number) {
      if (start === null) start = ts;
      const progress = Math.min((ts - start) / durationMs, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      setDisplay(Math.round(from + (target - from) * eased));
      if (progress < 1) raf = requestAnimationFrame(step);
    }
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }, [active, target, prefersReducedMotion]);

  const shown = active && prefersReducedMotion ? target : display;

  return (
    <div ref={ref} className="rounded-xl border border-neutral-200 bg-neutral-50 px-4 py-6 text-center">
      <div className="text-4xl font-bold text-brand-600">
        {prefix}
        {shown}
        {suffix}
      </div>
      <p className="mt-2 text-xs text-neutral-500">{label}</p>
    </div>
  );
}

export default function LandingPage() {
  const [authState, setAuthState] = useState<"loading" | "guest" | "onboarding" | "dashboard">("loading");
  const pipelineScrollRef = useRef<HTMLDivElement>(null);

  // Continuous auto-scroll, always running, no interaction of any kind
  // involved: the item list is rendered twice back-to-back (see
  // CAROUSEL_STEPS) so resetting scrollLeft by exactly half its total
  // width, once we've drifted past that halfway point, is an invisible
  // loop instead of a visible jump-cut back to the start.
  useEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const el = pipelineScrollRef.current;
    if (!el) return;

    const interval = setInterval(() => {
      el.scrollLeft += 1;
      if (el.scrollLeft >= el.scrollWidth / 2) {
        el.scrollLeft -= el.scrollWidth / 2;
      }
    }, 30);

    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    fetchCurrentUser().then((user) => {
      if (!user) {
        setAuthState("guest");
      } else if (!hasCompletedOnboarding(user)) {
        setAuthState("onboarding");
      } else {
        setAuthState("dashboard");
      }
    });
  }, []);

  const primaryHref = authState === "dashboard" ? "/dashboard" : authState === "onboarding" ? "/onboarding" : "/signup";
  const primaryLabel = authState === "dashboard" ? "Go to dashboard" : authState === "onboarding" ? "Finish setup" : "Start for free";

  return (
    <div className="min-h-screen bg-neutral-50 text-neutral-900">
      {/* Header */}
      <header className="sticky top-0 z-10 bg-neutral-50/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
          <Logo />
          <nav className="hidden items-center gap-6 text-sm text-neutral-500 sm:flex">
            <a href="#how-it-works" className="hover:text-neutral-900">How it works</a>
            <a href="#features" className="hover:text-neutral-900">Features</a>
            <a href="#proof" className="hover:text-neutral-900">Proof</a>
          </nav>
          <div className="flex items-center gap-6">
            {authState === "dashboard" ? (
              <Link href="/dashboard" className="rounded-lg bg-brand-900 px-4 py-2 text-sm font-medium text-white hover:bg-brand-800">
                Dashboard
              </Link>
            ) : (
              <>
                <Link href="/login" className="text-sm font-medium text-neutral-600 hover:text-neutral-900">
                  Log in
                </Link>
                <Link href="/signup" className="rounded-lg bg-brand-900 px-4 py-2 text-sm font-medium text-white hover:bg-brand-800">
                  Start now
                </Link>
              </>
            )}
          </div>
        </div>
      </header>

      {/* Hero */}
      <section className="relative overflow-hidden">
        <div className="bg-grid absolute inset-0 [mask-image:radial-gradient(ellipse_65%_70%_at_50%_10%,black,transparent)]" />
        <div className="relative mx-auto max-w-4xl px-6 pt-20 pb-16 text-center">
          <FloatingAvatars avatars={AVATARS} />

          <div className="mx-auto mb-6 inline-flex items-center gap-2 rounded-full border border-neutral-200 bg-white px-3 py-1 text-xs text-neutral-500">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
            Built on classical CS, proven not assumed
          </div>
          <h1 className="text-4xl font-semibold tracking-tight sm:text-6xl">
            One tool to <span className="text-brand-600">schedule</span>
            <br />
            every action item, automatically.
          </h1>
          <p className="mx-auto mt-6 max-w-2xl text-lg text-neutral-500">
            Cadenly listens to a recording, figures out who owns what and when it&apos;s due, and slots every action
            item into a real calendar — optimally, under real concurrent load, with no cloud dependency.
          </p>
          <div className="mt-10 flex items-center justify-center gap-3">
            <Link
              href={primaryHref}
              className="rounded-lg bg-brand-900 px-6 py-3 text-sm font-semibold text-white hover:bg-brand-800"
            >
              {authState === "loading" ? "…" : primaryLabel}
            </Link>
            {authState !== "dashboard" && (
              <Link
                href="/login"
                className="rounded-lg border border-neutral-300 bg-white px-6 py-3 text-sm font-semibold text-neutral-700 hover:border-neutral-400"
              >
                Log in
              </Link>
            )}
          </div>
        </div>
      </section>

      {/* Product previews - real dashboard components, not imagined ones */}
      <section id="features" className="bg-white px-6 py-20">
        <div className="mx-auto max-w-6xl">
        <div className="mb-10 text-center">
          <div className="mx-auto mb-4 inline-flex items-center gap-1.5 rounded-full border border-neutral-200 bg-white px-3 py-1 text-xs font-medium text-neutral-500">
            <LayoutGrid className="h-3.5 w-3.5" /> Features
          </div>
          <h2 className="text-4xl font-semibold tracking-tight">Your calendar, working the way it should</h2>
          <p className="mx-auto mt-3 max-w-xl text-neutral-500">
            A closer look at what Cadenly takes off your plate every single day.
          </p>
        </div>
        <div className="space-y-5">
          <div className="grid grid-cols-1 overflow-hidden rounded-xl border border-neutral-200 bg-neutral-100 lg:grid-cols-2">
            <div className="flex flex-col justify-center p-6">
              <h3 className="text-lg font-semibold">Tasks by day, at a glance</h3>
              <p className="mt-1.5 text-sm leading-relaxed text-neutral-600">
                Every placed task lands on the real board the instant the scheduler commits it — no manual
                re-entry, no separate calendar to keep in sync.
              </p>
              <Link
                href={primaryHref}
                className="mt-4 w-fit rounded-lg bg-brand-900 px-3.5 py-1.5 text-xs font-medium text-white hover:bg-brand-800"
              >
                See it live
              </Link>
            </div>
            <div className="bg-white p-5">
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium text-neutral-900">This week</span>
                <div className="flex -space-x-2">
                  {PREVIEW_AVATARS.map((a) => (
                    <div
                      key={a.initials}
                      className={`flex h-7 w-7 items-center justify-center rounded-full text-[10px] font-semibold ring-2 ring-white ${a.theme}`}
                    >
                      {a.initials}
                    </div>
                  ))}
                </div>
              </div>
              <div className="mt-5 flex h-28 items-end gap-2.5">
                {WEEKDAY_PREVIEW.map((d) => (
                  <div key={d.day} className="flex flex-1 flex-col items-center gap-1">
                    <div className="w-full rounded-t-md bg-orange-300" style={{ height: `${d.pct}%` }} />
                    <span className="text-[11px] text-neutral-500">{d.day}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>

          <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
            <div className="overflow-hidden rounded-xl border border-neutral-200">
              <div className="bg-neutral-100 p-5">
                <h3 className="text-base font-semibold">Live feed, not a polling loop</h3>
                <p className="mt-1.5 text-sm text-neutral-600">Pushed over WebSocket the instant a booking succeeds anywhere.</p>
              </div>
              <div className="space-y-2 bg-white p-3.5">
                {LIVE_FEED_PREVIEW.map((event) => (
                  <div key={event.name} className="flex items-center gap-3 rounded-lg border border-neutral-100 p-2 text-sm">
                    <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-xs font-semibold ${event.theme}`}>
                      {event.initials}
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-medium">{event.name}</p>
                      <p className="text-xs text-neutral-500">{event.time}</p>
                    </div>
                    <span className="shrink-0 text-xs text-neutral-400">{event.ago}</span>
                  </div>
                ))}
              </div>
            </div>

            <div className="overflow-hidden rounded-xl border border-neutral-200">
              <div className="p-5 bg-neutral-100">
                <h3 className="text-base font-semibold">Every outcome, reported honestly</h3>
                <p className="mt-1.5 text-sm text-neutral-600">Placed, rejected, or unresolved — never a silent guess.</p>
              </div>
              <div className="space-y-2 bg-white p-3.5">
                {OUTCOME_PREVIEW.map((o) => (
                  <div key={o.label} className="flex items-center justify-between rounded-lg border border-neutral-100 px-3 py-2 text-sm">
                    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${o.theme}`}>{o.label}</span>
                    <span className="font-semibold">{o.count}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
        </div>
      </section>

      {/* Pipeline cards */}
      <section id="how-it-works" className="mx-auto max-w-6xl px-6 py-20">
        <div className="mb-10 text-center">
          <div className="mx-auto mb-4 inline-flex items-center gap-1.5 rounded-full border border-neutral-200 bg-white px-3 py-1 text-xs font-medium text-neutral-500">
            <LayoutGrid className="h-3.5 w-3.5" /> How it works
          </div>
          <h2 className="text-4xl font-semibold tracking-tight">From recording to calendar, automatically</h2>
          <p className="mx-auto mt-3 max-w-xl text-neutral-500">Six steps, all handled the moment a meeting ends.</p>
        </div>

        <div
          ref={pipelineScrollRef}
          className="flex gap-4 overflow-x-hidden pb-2"
        >
          {CAROUSEL_STEPS.map((step, i) => (
            <div
              key={`${step.label}-${i}`}
              className={`flex aspect-square w-36 shrink-0 flex-col rounded-2xl ${step.theme} p-4`}
            >
              <step.icon className={`h-6 w-6 ${step.iconColor}`} strokeWidth={2} />
              <p className="mt-3 text-sm font-semibold">{step.label}</p>
              <p className="mt-1 text-xs text-neutral-600">{step.detail}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Dark proof + CTA band - one unified message: kicker, headline, stats as proof, single CTA */}
      <section id="proof" className="relative overflow-hidden bg-white px-26 py-20">
        <div className="bg-grid absolute inset-0 [mask-image:radial-gradient(ellipse_80%_80%_at_50%_0%,black,transparent)]" />
        <div className="relative overflow-hidden rounded-3xl border border-neutral-200 bg-neutral-50">
          <div className="grid grid-cols-1 gap-10 p-8 md:p-12 lg:grid-cols-2 lg:items-center">
            <div className="flex flex-col items-start justify-start">
              <div className="inline-flex items-center gap-1.5 rounded-full border border-neutral-200 bg-white px-3 py-1 text-xs font-medium text-neutral-500">
                <LayoutGrid className="h-3.5 w-3.5" /> Why teams trust Cadenly
              </div>
              <h2 className="mt-4 text-3xl font-semibold tracking-tight text-neutral-900 sm:text-4xl">
                Stop retyping action items into your calendar
              </h2>
              <p className="mt-3 max-w-md text-neutral-500">
                Sign up, tell us how you work, and let the scheduler take it from there.
              </p>
              <Link
                href={primaryHref}
                className="mt-8 inline-flex items-center gap-1.5 rounded-lg bg-brand-900 px-6 py-3 text-sm font-semibold text-white hover:bg-brand-800"
              >
                {authState === "loading" ? "…" : primaryLabel}
                <ArrowRight className="h-4 w-4" />
              </Link>
            </div>

            <div className="grid grid-cols-2 gap-4">
              {PROOF_STATS.map((stat) => (
                <ProofStat key={stat.label} {...stat} />
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="bg-white px-12 pt-14 pb-8">
        <div className="mx-auto flex max-w-6xl flex-col gap-10 sm:flex-row sm:justify-between">
          <div className="max-w-[32rem]">
            <Logo />
            <p className="mt-3 text-sm text-neutral-500">
              Turns a meeting recording into optimally scheduled,
              <br />
              conflict-free tasks — running fully locally.
            </p>
          </div>

          <div className="grid grid-cols-3 gap-8 sm:gap-10">
            <div>
              <h3 className="text-xs font-semibold tracking-wide text-neutral-500 uppercase">Product</h3>
              <ul className="mt-4 space-y-3 text-sm text-neutral-900">
                <li><a href="#how-it-works" className="hover:text-brand-700">How it works</a></li>
                <li><a href="#features" className="hover:text-brand-700">Features</a></li>
                <li><a href="#proof" className="hover:text-brand-700">Proof</a></li>
              </ul>
            </div>

            <div>
              <h3 className="text-xs font-semibold tracking-wide text-neutral-500 uppercase">Account</h3>
              <ul className="mt-4 space-y-3 text-sm text-neutral-900">
                <li><Link href="/login" className="hover:text-brand-700">Log in</Link></li>
                <li><Link href="/signup" className="hover:text-brand-700">Sign up</Link></li>
                <li><Link href="/dashboard" className="hover:text-brand-700">Dashboard</Link></li>
              </ul>
            </div>

            <div>
              <h3 className="text-xs font-semibold tracking-wide text-neutral-500 uppercase">Stack</h3>
              <ul className="mt-4 space-y-3 text-sm text-neutral-900">
                <li>Java · Spring Boot</li>
                <li>Python · FastAPI</li>
                <li>Next.js · Postgres</li>
              </ul>
            </div>
          </div>
        </div>

        <div className="mx-auto mt-12 flex max-w-6xl flex-col items-center justify-between gap-4 border-t border-neutral-200 pt-6 text-xs text-neutral-500 sm:flex-row">
          <span>© {new Date().getFullYear()} Cadenly. All rights reserved.</span>
          <span>scheduler-engine · recording-pipeline · dashboard</span>
        </div>
      </footer>
    </div>
  );
}
