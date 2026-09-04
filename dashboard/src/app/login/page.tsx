"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { apiFetch, hasCompletedOnboarding, type CurrentUser } from "@/lib/api";
import { Logo } from "@/components/Logo";
import { FloatingAvatars, AUTH_PAGE_AVATARS } from "@/components/FloatingAvatars";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);

    try {
      const response = await apiFetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });

      if (response.status === 401) {
        setError("Incorrect email or password.");
        return;
      }
      if (!response.ok) {
        setError("Something went wrong logging in.");
        return;
      }

      const user: CurrentUser = await response.json();
      router.push(hasCompletedOnboarding(user) ? "/dashboard" : "/onboarding");
    } catch {
      setError("Could not reach scheduler-engine on localhost:8080 - is it running?");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-neutral-50 px-6 text-neutral-900">
      <div className="bg-grid absolute inset-0 [mask-image:radial-gradient(ellipse_70%_70%_at_50%_50%,black,transparent)]" />
      <FloatingAvatars avatars={AUTH_PAGE_AVATARS} />

      <div className="relative w-full max-w-sm">
        <Link href="/" className="mb-8 flex items-center justify-center">
          <Logo />
        </Link>

        <div className="rounded-2xl border border-neutral-200 bg-white p-6">
          <h1 className="text-xl font-semibold">Log in</h1>
          
          <form onSubmit={onSubmit} className="mt-6 space-y-4">
            <div>
              <label className="mb-1 block text-xs font-medium text-neutral-500">Email</label>
              <input
                required
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full rounded-lg border border-neutral-300 px-3 py-2 text-sm outline-none focus:border-neutral-500"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-neutral-500">Password</label>
              <input
                required
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full rounded-lg border border-neutral-300 px-3 py-2 text-sm outline-none focus:border-neutral-500"
              />
            </div>

            {error && <p className="text-xs text-red-600">{error}</p>}

            <button
              disabled={submitting}
              className="w-full rounded-lg bg-brand-900 px-4 py-2.5 text-sm font-semibold text-white hover:bg-brand-800 disabled:opacity-50"
            >
              {submitting ? "Logging in…" : "Log in"}
            </button>
          </form>
        </div>

        <p className="mt-6 text-center text-sm text-neutral-500">
          Don&apos;t have an account?{" "}
          <Link href="/signup" className="font-medium text-brand-700 hover:underline">
            Sign up
          </Link>
        </p>
      </div>
    </div>
  );
}
