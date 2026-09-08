"use client";

import Link from "next/link";
import { LayoutDashboard, Users, Calendar, Settings, UploadCloud } from "lucide-react";
import { Logo } from "@/components/Logo";

type NavKey = "dashboard" | "calendar" | "upload" | "settings";

const NAV_ITEMS: { key: NavKey; label: string; href: string; icon: typeof LayoutDashboard }[] = [
  { key: "dashboard", label: "Dashboard", href: "/dashboard", icon: LayoutDashboard },
  { key: "calendar", label: "Schedule", href: "/calendar", icon: Calendar },
  { key: "upload", label: "Upload notes", href: "/upload", icon: UploadCloud },
  { key: "settings", label: "Settings", href: "/settings", icon: Settings },
];

export function Sidebar({ active }: { active: NavKey }) {
  return (
    <aside className="flex w-60 shrink-0 flex-col border-r border-neutral-200 bg-white p-5">
      <div className="mb-8 px-1">
        <Logo />
      </div>

      <nav className="flex flex-col gap-1 text-sm">
        {NAV_ITEMS.map((item) => {
          const Icon = item.icon;
          const isActive = item.key === active;
          return (
            <Link
              key={item.key}
              href={item.href}
              className={`flex items-center gap-2 rounded-lg px-3 py-2 font-medium ${
                isActive ? "bg-sky-300 text-white" : "text-neutral-600 hover:bg-neutral-100"
              }`}
            >
              <Icon size={16} /> {item.label}
            </Link>
          );
        })}
        <span className="flex cursor-not-allowed items-center gap-2 rounded-lg px-3 py-2 text-neutral-400">
          <Users size={16} /> People (soon)
        </span>
      </nav>
      <div className="mt-auto pt-4 text-xs text-neutral-400">© 2026 Cadenly</div>
    </aside>
  );
}
