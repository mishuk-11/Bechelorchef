"use client";

import { useQuery } from "@tanstack/react-query";
import { Search, X } from "lucide-react";
import { useEffect, useId, useState } from "react";
import { catalogApi } from "@/lib/api/endpoints";
import type { Ingredient } from "@/types/api";

function useDebounced<T>(value: T, ms = 250) {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}

/** Searches the active ingredient catalogue (ARIA combobox). Used by the matcher and the editor. */
export function IngredientSearch({ onSelect, excludeIds = [], label = "Search ingredients", inputId }: {
  onSelect: (ingredient: Ingredient) => void;
  excludeIds?: number[];
  label?: string;
  inputId?: string;
}) {
  const generated = useId();
  const id = inputId ?? `ingredient-search-${generated}`;
  const listId = `${id}-list`;
  const [text, setText] = useState("");
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const query = useDebounced(text.trim());
  const { data, isFetching, isError } = useQuery({
    queryKey: ["ingredients", query],
    queryFn: () => catalogApi.ingredients(query, 0, 12),
    enabled: query.length > 0,
  });
  const options = (data?.items ?? []).filter((i) => !excludeIds.includes(i.id));

  function choose(ingredient: Ingredient) {
    onSelect(ingredient);
    setText("");
    setOpen(false);
    setActive(0);
  }

  return (
    <div className="relative">
      <label htmlFor={id} className="sr-only">{label}</label>
      <div className="relative">
        <Search aria-hidden className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />
        <input
          id={id}
          role="combobox"
          aria-expanded={open && options.length > 0}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={open && options[active] ? `${listId}-${options[active].id}` : undefined}
          autoComplete="off"
          placeholder="Type an ingredient, e.g. egg"
          value={text}
          onChange={(e) => {
            setText(e.target.value);
            setOpen(true);
            setActive(0);
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => setTimeout(() => setOpen(false), 150)}
          onKeyDown={(e) => {
            if (e.key === "ArrowDown") {
              e.preventDefault();
              setActive((a) => Math.min(a + 1, options.length - 1));
            } else if (e.key === "ArrowUp") {
              e.preventDefault();
              setActive((a) => Math.max(a - 1, 0));
            } else if (e.key === "Enter" && open && options[active]) {
              e.preventDefault();
              choose(options[active]);
            } else if (e.key === "Escape") {
              setOpen(false);
            }
          }}
          className="h-11 w-full rounded-lg border border-line bg-surface pl-9 pr-3 text-sm focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/30"
        />
      </div>
      {open && query.length > 0 && (
        <ul id={listId} role="listbox" className="absolute z-30 mt-1 max-h-64 w-full overflow-auto rounded-lg border border-line bg-surface py-1 shadow-lg">
          {isError && <li className="px-3 py-2 text-sm text-danger">Could not load ingredients.</li>}
          {!isError && isFetching && options.length === 0 && <li className="px-3 py-2 text-sm text-muted">Searching…</li>}
          {!isFetching && !isError && options.length === 0 && <li className="px-3 py-2 text-sm text-muted">No matching ingredient.</li>}
          {options.map((o, i) => (
            <li key={o.id} id={`${listId}-${o.id}`} role="option" aria-selected={i === active}
              onMouseDown={(e) => {
                e.preventDefault();
                choose(o);
              }}
              className={`flex cursor-pointer justify-between px-3 py-2 text-sm ${i === active ? "bg-brand-soft" : ""}`}>
              <span>{o.name}</span>
              {o.category && <span className="text-xs text-muted">{o.category}</span>}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function IngredientChips({ items, onRemove }: { items: { id: number; name: string }[]; onRemove: (id: number) => void }) {
  if (items.length === 0) return null;
  return (
    <ul className="flex flex-wrap gap-2" aria-label="Selected ingredients">
      {items.map((i) => (
        <li key={i.id}>
          <button type="button" onClick={() => onRemove(i.id)} aria-label={`Remove ${i.name}`}
            className="inline-flex items-center gap-1 rounded-full bg-herb-soft px-3 py-1 text-sm text-herb hover:bg-green-100">
            {i.name} <X aria-hidden className="h-3.5 w-3.5" />
          </button>
        </li>
      ))}
    </ul>
  );
}
