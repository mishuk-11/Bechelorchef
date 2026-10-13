import type { Difficulty, RecipeStatus, TimeCategory } from "@/types/api";

export const DISPLAY_TIME_ZONE = "Asia/Dhaka";

const money = new Intl.NumberFormat("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** Money is always shown as ৳80.00 (BDT). Values arrive from the API already rounded. */
export function formatBdt(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return "—";
  return `৳${money.format(Number(value))}`;
}

/** Quantities: up to 3 decimals, trailing zeros removed (0.750 -> 0.75, 4.000 -> 4). */
export function formatQuantity(value: number): string {
  const rounded = Math.round(Number(value) * 1000) / 1000;
  return String(rounded);
}

export function formatMinutes(mins: number): string {
  return `${mins} min`;
}

/** Same thresholds as the backend: Express ≤ 15, Bachelor Quick 16–20, Extended 21–60. */
export function timeCategory(totalMins: number): TimeCategory | null {
  if (totalMins < 1 || totalMins > 60) return null;
  if (totalMins <= 15) return "EXPRESS";
  if (totalMins <= 20) return "BACHELOR_QUICK";
  return "EXTENDED";
}

export const TIME_CATEGORY_LABEL: Record<TimeCategory, string> = {
  EXPRESS: "Express",
  BACHELOR_QUICK: "Bachelor Quick",
  EXTENDED: "Extended",
};

export const DIFFICULTY_LABEL: Record<Difficulty, string> = { EASY: "Easy", MEDIUM: "Medium", HARD: "Hard" };

export const STATUS_LABEL: Record<RecipeStatus, string> = {
  DRAFT: "Draft",
  PENDING: "Pending review",
  APPROVED: "Published",
  REJECTED: "Rejected",
  ARCHIVED: "Archived",
};

export function formatDate(iso: string | null | undefined, withTime = false): string {
  if (!iso) return "—";
  const options: Intl.DateTimeFormatOptions = withTime
    ? { dateStyle: "medium", timeStyle: "short", timeZone: DISPLAY_TIME_ZONE }
    : { dateStyle: "medium", timeZone: DISPLAY_TIME_ZONE };
  return new Intl.DateTimeFormat("en-GB", options).format(new Date(iso));
}

/** Current month in Dhaka as YYYY-MM. */
export function currentMonth(now: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone: DISPLAY_TIME_ZONE, year: "numeric", month: "2-digit" }).formatToParts(now);
  const y = parts.find((p) => p.type === "year")?.value;
  const m = parts.find((p) => p.type === "month")?.value;
  return `${y}-${m}`;
}

/** YYYY-MM-DD in Dhaka for a date offset by `days`. */
export function dhakaDate(offsetDays = 0, now: Date = new Date()): string {
  const d = new Date(now.getTime() + offsetDays * 86_400_000);
  return new Intl.DateTimeFormat("en-CA", { timeZone: DISPLAY_TIME_ZONE }).format(d);
}

export function formatRating(avg: number, count: number): string {
  if (!count) return "No ratings yet";
  return `${Number(avg).toFixed(1)} (${count})`;
}

/** Only https URLs are rendered as images or links to user content. */
export function safeHttpsUrl(url: string | null | undefined): string | null {
  if (!url) return null;
  try {
    const parsed = new URL(url);
    return parsed.protocol === "https:" ? parsed.toString() : null;
  } catch {
    return null;
  }
}

/** Only allow same-site relative paths as post-login redirects (prevents open redirects). */
export function safeNext(next: string | null, fallback = "/"): string {
  return next && next.startsWith("/") && !next.startsWith("//") && !next.startsWith("/\\") ? next : fallback;
}
