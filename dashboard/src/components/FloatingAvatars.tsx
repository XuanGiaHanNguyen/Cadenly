"use client";

import type { CSSProperties } from "react";

export type AvatarSpec = {
  initials: string;
  theme: string;
  position: string;
  driftX: number;
  driftY: number;
  duration: number;
  delay: number;
};

/** Cycle through these pastel themes when generating a batch of avatars. */
export const AVATAR_THEMES = [
  "bg-violet-100 text-violet-900",
  "bg-orange-100 text-orange-900",
  "bg-lime-100 text-lime-900",
  "bg-sky-100 text-sky-900",
];

/**
 * 8 avatars scattered across the whole page around a centered auth card -
 * spread across top/upper-mid/lower-mid/bottom bands at varied horizontal
 * offsets, not just clustered along the left/right edges, so the card ends
 * up surrounded by a loose cloud rather than flanked by two columns.
 * login/signup use this shared set so both pages look identical.
 */
export const AUTH_PAGE_AVATARS: AvatarSpec[] = [
  { initials: "SK", theme: AVATAR_THEMES[0], position: "top-[6%] left-[18%]", driftX: 14, driftY: 10, duration: 8, delay: 0 },
  { initials: "JD", theme: AVATAR_THEMES[1], position: "top-[9%] right-[6%]", driftX: 10, driftY: 14, duration: 10, delay: 1.2 },
  { initials: "PR", theme: AVATAR_THEMES[2], position: "top-[14%] right-[16%]", driftX: 12, driftY: 9, duration: 9, delay: 2.1 },
  { initials: "AM", theme: AVATAR_THEMES[3], position: "top-[42%] left-[7%]", driftX: 9, driftY: 13, duration: 11, delay: 0.6 },
  { initials: "TC", theme: AVATAR_THEMES[0], position: "top-[46%] right-[9%]", driftX: 13, driftY: 8, duration: 10.5, delay: 1.8 },
  { initials: "RL", theme: AVATAR_THEMES[1], position: "bottom-[32%] left-[24%]", driftX: 8, driftY: 12, duration: 8.5, delay: 2.7 },
  { initials: "MN", theme: AVATAR_THEMES[2], position: "bottom-[10%] left-[6%]", driftX: 16, driftY: 8, duration: 9.5, delay: 0.9 },
  { initials: "EW", theme: AVATAR_THEMES[3], position: "bottom-[8%] right-[19%]", driftX: 9, driftY: 12, duration: 11.5, delay: 1.5 },
];

function FloatingAvatar({ initials, theme, position, driftX, driftY, duration, delay }: AvatarSpec) {
  return (
    <div className={`absolute hidden ${position} sm:block`}>
      <div
        className="animate-drift relative"
        style={
          {
            "--drift-x": `${driftX}px`,
            "--drift-y": `${driftY}px`,
            "--drift-duration": `${duration}s`,
            "--drift-delay": `${delay}s`,
          } as CSSProperties
        }
      >
        <div className={`flex h-10 w-10 items-center justify-center rounded-full text-sm font-semibold ring-4 ring-white shadow-sm ${theme}`}>
          {initials}
        </div>
        <svg viewBox="0 0 16 16" className="absolute -right-5 -bottom-5 h-5 w-7 rotate-12 text-brand-700 drop-shadow-sm" fill="currentColor">
          <path d="M1 1l6 13 2-5 5-2z" />
        </svg>
      </div>
    </div>
  );
}

/**
 * Renders a batch of ambient-drifting avatar bubbles (each with a small
 * cursor accent), positioned absolutely - the parent must be `relative` (or
 * `fixed`) with `overflow-hidden`. Each avatar drifts within its own small
 * area at a different amplitude/speed/start offset so the group wanders out
 * of sync instead of bobbing together like a single unit.
 */
export function FloatingAvatars({ avatars }: { avatars: AvatarSpec[] }) {
  return (
    <>
      {avatars.map((a, i) => (
        <FloatingAvatar key={`${a.initials}-${i}`} {...a} />
      ))}
    </>
  );
}
