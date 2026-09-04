"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Link2, CalendarPlus, ArrowLeft } from "lucide-react";
import { apiFetch, fetchCurrentUser, hasCompletedOnboarding } from "@/lib/api";
import { Logo } from "@/components/Logo";
import { FloatingAvatars, AUTH_PAGE_AVATARS } from "@/components/FloatingAvatars";

const OCCUPATIONS = [
  "Engineering manager",
  "Software engineer",
  "Product manager",
  "Designer",
  "Founder / executive",
  "Consultant",
  "Student",
  "Other",
];

type CalendarChoice = "google" | "manual" | null;

export default function OnboardingPage() {
  const router = useRouter();
  const [checking, setChecking] = useState(true);
  const [step, setStep] = useState<1 | 2>(1);

  const [occupation, setOccupation] = useState("");
  const [customOccupation, setCustomOccupation] = useState("");
  const [calendarChoice, setCalendarChoice] = useState<CalendarChoice>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchCurrentUser().then((user) => {
      if (!user) {
        router.replace("/login");
        return;
      }
      if (hasCompletedOnboarding(user)) {
        router.replace("/dashboard");
        return;
      }
      setChecking(false);
    });
  }, [router]);

  const resolvedOccupation = occupation === "Other" ? customOccupation.trim() : occupation;
  const canContinueStep1 = resolvedOccupation.length > 0;

  async function finish(choice: Exclude<CalendarChoice, null>) {
    setCalendarChoice(choice);
    setSubmitting(true);
    setError(null);

    try {
      const response = await apiFetch("/api/auth/onboarding", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ occupation: resolvedOccupation, calendarPreference: choice }),
      });
      if (!response.ok) {
        setError("Something went wrong saving your setup.");
        setSubmitting(false);
        return;
      }
      router.push("/dashboard");
    } catch {
      setError("Could not reach scheduler-engine on localhost:8080 - is it running?");
      setSubmitting(false);
    }
  }

  if (checking) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-neutral-50 text-sm text-neutral-500">
        Checking session…
      </div>
    );
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-neutral-50 px-6 text-neutral-900">
      <div className="bg-grid absolute inset-0 [mask-image:radial-gradient(ellipse_70%_70%_at_50%_50%,black,transparent)]" />
      <FloatingAvatars avatars={AUTH_PAGE_AVATARS} />

      <div className="relative w-full max-w-lg">
        <div className="mb-6 flex items-center justify-center">
          <Logo />
        </div>

        <div className="rounded-2xl border border-neutral-200 bg-white p-6">
          <div className="mb-6 flex items-center gap-2">
            <div className={`h-1.5 flex-1 rounded-full ${step >= 1 ? "bg-brand-900" : "bg-neutral-200"}`} />
            <div className={`h-1.5 flex-1 rounded-full ${step >= 2 ? "bg-brand-900" : "bg-neutral-200"}`} />
          </div>

          {step === 1 && (
            <>
              <h1 className="text-xl font-semibold">What do you do?</h1>
              <p className="mt-1 text-sm text-neutral-500">Helps us tune how tasks and priorities get suggested.</p>

              <div className="mt-6 grid grid-cols-2 gap-2">
                {OCCUPATIONS.map((option) => (
                  <button
                    key={option}
                    type="button"
                    onClick={() => setOccupation(option)}
                    className={`rounded-lg border px-3 py-2.5 text-left text-sm font-medium transition ${
                      occupation === option
                        ? "border-brand-900 bg-brand-900 text-white"
                        : "border-neutral-200 text-neutral-700 hover:border-neutral-400"
                    }`}
                  >
                    {option}
                  </button>
                ))}
              </div>

              {occupation === "Other" && (
                <input
                  autoFocus
                  placeholder="Tell us what you do"
                  value={customOccupation}
                  onChange={(e) => setCustomOccupation(e.target.value)}
                  className="mt-3 w-full rounded-lg border border-neutral-300 px-3 py-2 text-sm outline-none focus:border-neutral-500"
                />
              )}

              <button
                disabled={!canContinueStep1}
                onClick={() => setStep(2)}
                className="mt-6 w-full rounded-lg bg-brand-900 px-4 py-2.5 text-sm font-semibold text-white hover:bg-brand-800 disabled:opacity-40"
              >
                Continue
              </button>
            </>
          )}

          {step === 2 && (
            <>
              <h1 className="text-xl font-semibold">Bring your calendar</h1>
              <p className="mt-1 text-sm text-neutral-500">Import what you already have, or start clean with Cadenly.</p>

              <div className="mt-6 space-y-3">
                <button
                  type="button"
                  disabled={submitting}
                  onClick={() => finish("google")}
                  className="flex w-full items-center gap-3 rounded-xl border border-neutral-200 p-4 text-left hover:border-neutral-400 disabled:opacity-50"
                >
                  <Link2 className="h-6 w-6 shrink-0 text-neutral-500" />
                  <span>
                    <span className="block text-sm font-semibold">Connect Google Calendar</span>
                    <span className="block text-xs text-neutral-500">
                      {submitting && calendarChoice === "google" ? "Saving…" : "Sync your existing events (coming soon — recorded as your preference for now)"}
                    </span>
                  </span>
                </button>

                <button
                  type="button"
                  disabled={submitting}
                  onClick={() => finish("manual")}
                  className="flex w-full items-center gap-3 rounded-xl border border-neutral-200 p-4 text-left hover:border-neutral-400 disabled:opacity-50"
                >
                  <CalendarPlus className="h-6 w-6 shrink-0 text-neutral-500" />
                  <span>
                    <span className="block text-sm font-semibold">Create schedule</span>
                    <span className="block text-xs text-neutral-500">
                      {submitting && calendarChoice === "manual" ? "Saving…" : "Start fresh — build your calendar inside Cadenly"}
                    </span>
                  </span>
                </button>
              </div>

              {error && <p className="mt-4 text-xs text-red-600">{error}</p>}

              <button
                onClick={() => setStep(1)}
                className="mt-6 inline-flex items-center gap-1 text-xs font-medium text-neutral-400 hover:text-neutral-600"
              >
                <ArrowLeft className="h-3.5 w-3.5" /> Back
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
