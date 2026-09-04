"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { apiFetch } from "@/lib/api";
import { Logo } from "@/components/Logo";
import { FloatingAvatars, AUTH_PAGE_AVATARS } from "@/components/FloatingAvatars";

export default function SignupPage() {
  const router = useRouter();
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);

    try {
      const response = await apiFetch("/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password, displayName }),
      });

      if (response.status === 409) {
        setError("An account with that email already exists.");
        return;
      }
      if (!response.ok) {
        setError("Something went wrong signing up.");
        return;
      }

      // Fresh accounts have never onboarded - always go there next.
      router.push("/onboarding");
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
          <h1 className="text-xl font-semibold">Create your account</h1>
          <p className="mt-1 text-sm text-neutral-500">Free, local, no card required.</p>

          <form onSubmit={onSubmit} className="mt-6 space-y-4">
            <div>
              <label className="mb-1 block text-xs font-medium text-neutral-500">Name</label>
              <input
                required
                autoComplete="name"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                className="w-full rounded-lg border border-neutral-300 px-3 py-2 text-sm outline-none focus:border-neutral-500"
              />
            </div>
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
                minLength={8}
                autoComplete="new-password"
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
              {submitting ? "Creating account…" : "Sign up"}
            </button>
          </form>
        </div>

        <p className="mt-6 text-center text-sm text-neutral-500">
          Already have an account?{" "}
          <Link href="/login" className="font-medium text-brand-700 hover:underline">
            Log in
          </Link>
        </p>
      </div>
    </div>
  );
}
