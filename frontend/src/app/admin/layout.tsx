"use client";

import clsx from "clsx";
import { BarChart3, Carrot, FileUp, LayoutDashboard, ShieldCheck, UtensilsCrossed, Users } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { RequireAdmin } from "@/components/layout/guards";

const nav = [
  { href: "/admin", label: "Dashboard", icon: LayoutDashboard },
  { href: "/admin/recipes", label: "Recipes", icon: UtensilsCrossed },
  { href: "/admin/ingredients", label: "Ingredients", icon: Carrot },
  { href: "/admin/moderation", label: "Moderation", icon: ShieldCheck },
  { href: "/admin/imports", label: "Bulk import", icon: FileUp },
  { href: "/admin/users", label: "Users", icon: Users },
  { href: "/admin/analytics", label: "Analytics", icon: BarChart3 },
];

export default function AdminLayout({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  return (
    <RequireAdmin>
      <div className="mx-auto flex max-w-7xl flex-col gap-6 px-4 py-6 lg:flex-row">
        <aside className="lg:w-56 lg:shrink-0">
          <p className="mb-2 px-3 text-xs font-semibold uppercase tracking-wide text-muted">Bachelor Chef Admin</p>
          <nav aria-label="Admin" className="flex gap-1 overflow-x-auto pb-2 lg:flex-col lg:overflow-visible">
            {nav.map(({ href, label, icon: Icon }) => {
              const active = href === "/admin" ? pathname === "/admin" : pathname.startsWith(href);
              return (
                <Link key={href} href={href} aria-current={active ? "page" : undefined}
                  className={clsx("flex shrink-0 items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium",
                    active ? "bg-brand text-white" : "text-ink hover:bg-sunken")}>
                  <Icon aria-hidden className="h-4 w-4" /> {label}
                </Link>
              );
            })}
          </nav>
        </aside>
        <div className="min-w-0 flex-1">{children}</div>
      </div>
    </RequireAdmin>
  );
}
