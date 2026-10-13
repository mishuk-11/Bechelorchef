"use client";

import { useEffect, useMemo, useRef, useState } from "react";

/**
 * Hidden fun: type a secret word into the "Find a meal" ingredient search or the Recipes search.
 *   inzu         → a motorbike rides across the screen from right to left
 *   cherryberry  → cherries fall from the top
 *   whobuildyou  → movie-style credits roll from bottom to top
 * Purely decorative: click-through, hidden from screen readers, never changes the search, and
 * skipped entirely for people who ask for reduced motion. Esc ends it early.
 */
type Kind = "bike" | "cherries" | "credits";

const SECRETS: Record<string, Kind> = {
  inzu: "bike",
  cherryberry: "cherries",
  whobuildyou: "credits",
  whobuiltyou: "credits",
};

const WATCHED_INPUTS = new Set(["pantry-search", "recipe-search"]);
const DURATION_MS: Record<Kind, number> = { bike: 3600, cherries: 5200, credits: 12500 };

export function EasterEggs() {
  const [active, setActive] = useState<{ kind: Kind; id: number } | null>(null);
  const lastWord = useRef<string | null>(null);

  useEffect(() => {
    function onInput(e: Event) {
      const el = e.target;
      if (!(el instanceof HTMLInputElement) || !WATCHED_INPUTS.has(el.id)) return;
      const word = el.value.toLowerCase().replace(/\s+/g, "");
      const kind = SECRETS[word];
      if (!kind) {
        lastWord.current = null; // clearing or changing the text re-arms the egg
        return;
      }
      if (lastWord.current === word) return; // play once per match
      lastWord.current = word;
      if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
      setActive({ kind, id: Date.now() });
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") setActive(null);
    }
    document.addEventListener("input", onInput, true);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("input", onInput, true);
      document.removeEventListener("keydown", onKey);
    };
  }, []);

  useEffect(() => {
    if (!active) return;
    const timer = window.setTimeout(() => setActive(null), DURATION_MS[active.kind]);
    return () => window.clearTimeout(timer);
  }, [active]);

  if (!active) return null;
  return (
    <div aria-hidden className="pointer-events-none fixed inset-0 z-[70] overflow-hidden" data-testid={`easter-egg-${active.kind}`}>
      {active.kind === "bike" && <Bike />}
      {active.kind === "cherries" && <Cherries seed={active.id} />}
      {active.kind === "credits" && <Credits />}
    </div>
  );
}

/** 🏍️ faces right on Windows/Android emoji fonts and left on Apple's, so mirror it unless on an Apple device. */
const facesRightByDefault = () => !/Mac|iPhone|iPad|iPod/i.test(navigator.userAgent);

function Bike() {
  const [mirror] = useState(facesRightByDefault);
  return (
    <div className="egg-bike absolute top-[58%] flex items-end gap-1 text-6xl sm:text-7xl">
      <span className="inline-block" style={mirror ? { transform: "scaleX(-1)" } : undefined}>
        <span className="egg-bike-rider inline-block">🏍️</span>
      </span>
      <span className="inline-block text-3xl opacity-70">💨</span>
    </div>
  );
}

/** Small deterministic pseudo-random generator, so one run's cherries are stable across re-renders. */
function makeRandom(seed: number) {
  let state = (seed % 2147483646) + 1;
  return () => {
    state = (state * 16807) % 2147483647;
    return state / 2147483647;
  };
}

function Cherries({ seed }: { seed: number }) {
  const cherries = useMemo(() => {
    const rand = makeRandom(seed);
    return Array.from({ length: 28 }, (_, i) => ({
      id: i,
      left: rand() * 96,
      size: 22 + rand() * 26,
      delay: rand() * 1.6,
      duration: 2.4 + rand() * 1.6,
      spin: (rand() * 2 - 1) * 540,
    }));
  }, [seed]);
  return (
    <>
      {cherries.map((c) => (
        <span key={c.id} className="egg-cherry absolute -top-16"
          style={{
            left: `${c.left}%`, fontSize: c.size, animationDelay: `${c.delay}s`, animationDuration: `${c.duration}s`,
            ["--egg-spin" as string]: `${c.spin}deg`,
          }}>
          🍒
        </span>
      ))}
    </>
  );
}

function Credits() {
  return (
    <div className="egg-credits-backdrop absolute inset-0 bg-[#0b0d0c]/90">
      <div className="egg-credits absolute inset-x-0 flex flex-col items-center gap-10 px-6 text-center text-white">
        <p className="text-sm uppercase tracking-[0.5em] text-white/60">A Bachelor Chef production</p>
        <h2 className="font-serif text-5xl font-bold tracking-wide sm:text-6xl">Bachelor Chef</h2>
        <div className="flex flex-col gap-2">
          <p className="text-xs uppercase tracking-[0.4em] text-white/60">Created &amp; built by</p>
          <p className="text-3xl font-semibold tracking-wide">Inzamam Islam</p>
        </div>
        <div className="flex flex-col gap-2">
          <p className="text-xs uppercase tracking-[0.4em] text-white/60">GitHub</p>
          <p className="font-mono text-2xl">mishuk-11</p>
        </div>
        <p className="pt-6 text-sm text-white/70">© {new Date().getFullYear()} Inzamam Islam. All rights reserved.</p>
        <p className="pt-10 text-lg italic text-white/80">Thanks for cooking with us.</p>
      </div>
    </div>
  );
}
