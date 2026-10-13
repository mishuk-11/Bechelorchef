import clsx from "clsx";
import { BadgeCheck, Clock, Soup, Users } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { Badge, Stars } from "@/components/ui/display";
import { DIFFICULTY_LABEL, STATUS_LABEL, formatBdt, safeHttpsUrl } from "@/lib/format";
import type { RecipeSummary, TimeCategory } from "@/types/api";

const categoryTone: Record<TimeCategory, "herb" | "saffron" | "neutral"> = {
  EXPRESS: "herb",
  BACHELOR_QUICK: "saffron",
  EXTENDED: "neutral",
};

/** Renders a recipe image only for valid https URLs; otherwise a neutral placeholder. */
export function RecipeImage({ url, title, className }: { url: string | null; title: string; className?: string }) {
  const safe = safeHttpsUrl(url);
  if (!safe) {
    return (
      <div className={clsx("flex items-center justify-center bg-gradient-to-br from-brand-soft to-saffron-soft text-brand", className)} aria-hidden>
        <Soup className="h-10 w-10 opacity-70" />
      </div>
    );
  }
  // eslint-disable-next-line @next/next/no-img-element -- host allow-list is enforced by the API
  return <img src={safe} alt={title} loading="lazy" className={clsx("object-cover", className)} referrerPolicy="no-referrer" />;
}

export function RecipeCard({ recipe, footer, showStatus }: { recipe: RecipeSummary; footer?: ReactNode; showStatus?: boolean }) {
  return (
    <article className="group flex flex-col overflow-hidden rounded-2xl border border-line bg-surface shadow-sm transition-shadow hover:shadow-md" data-testid="recipe-card">
      <Link href={`/recipes/${recipe.id}`} className="flex flex-1 flex-col">
        <RecipeImage url={recipe.imageUrl} title={recipe.title} className="aspect-[16/10] w-full" />
        <div className="flex flex-1 flex-col gap-2 p-4">
          <div className="flex flex-wrap items-center gap-1.5">
            <Badge tone={categoryTone[recipe.timeCategory]}>
              <Clock aria-hidden className="h-3 w-3" /> {recipe.totalTimeMins} min · {recipe.timeCategoryLabel}
            </Badge>
            {recipe.official && (
              <Badge tone="brand"><BadgeCheck aria-hidden className="h-3 w-3" /> Official</Badge>
            )}
            {showStatus && recipe.status !== "APPROVED" && <Badge tone="info">{STATUS_LABEL[recipe.status]}</Badge>}
          </div>
          <h3 className="text-base font-semibold leading-snug group-hover:text-brand">{recipe.title}</h3>
          <div className="mt-auto flex flex-wrap items-center justify-between gap-2 text-sm text-muted">
            <span className="font-semibold text-ink">{formatBdt(recipe.estimatedCostBdt)}</span>
            <span className="inline-flex items-center gap-1"><Users aria-hidden className="h-3.5 w-3.5" /> {recipe.baseServings}</span>
            <span>{DIFFICULTY_LABEL[recipe.difficulty]}</span>
          </div>
          <div className="flex items-center gap-2 text-xs text-muted">
            {recipe.ratingCount > 0 ? (
              <>
                <Stars value={Number(recipe.ratingAvg)} /> {Number(recipe.ratingAvg).toFixed(1)} ({recipe.ratingCount})
              </>
            ) : (
              "No ratings yet"
            )}
            {!recipe.official && recipe.author && <span className="ml-auto truncate">by {recipe.author.displayName}</span>}
          </div>
        </div>
      </Link>
      {footer && <div className="border-t border-line p-3">{footer}</div>}
    </article>
  );
}

export function RecipeGrid({ children }: { children: ReactNode }) {
  return <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">{children}</div>;
}
