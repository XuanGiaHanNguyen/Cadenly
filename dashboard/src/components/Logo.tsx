export function Logo({
  className = "",
  iconOnly = false,
  variant = "default",
}: {
  className?: string;
  iconOnly?: boolean;
  variant?: "default" | "light";
}) {
  const src = variant === "light" ? "/logo-white.png" : "/logo.png";
  return (
    <span className={`inline-flex items-center gap-2 ${className}`}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={src} alt="Cadenly" className="h-6 w-auto" />
      {!iconOnly && <span className="text-lg font-semibold">Cadenly</span>}
    </span>
  );
}
