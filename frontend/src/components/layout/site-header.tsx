"use client";

import clsx from "clsx";
import { ChefHat, LogOut, Menu, Shield, X } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { useAuth } from "@/features/auth/auth-provider";
import { ButtonLink } from "@/components/ui/button";

const publicLinks = [
  { href: "/recipes", label: "Recipes" },
  { href: "/match", label: "Find a meal" },
];
const userLinks = [
  { href: "/submit", label: "Submit" },
  { href: "/cookbook", label: "Cookbook" },
  { href: "/cooking-history", label: "Recipes Done" },
  { href: "/goals", label: "Goals" },
];

export function SiteHeader() {
  const { status, user, isAdmin, logout } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const links = [...publicLinks, ...(status === "authenticated" ? userLinks : [])];

  const navLink = (href: string, label: string) => (
    <Link key={href} href={href} onClick={() => setOpen(false)}
      aria-current={pathname.startsWith(href) ? "page" : undefined}
      className={clsx("rounded-lg px-3 py-2 text-sm font-medium hover:bg-sunken", pathname.startsWith(href) ? "text-brand" : "text-ink")}>
      {label}
    </Link>
  );

  async function handleLogout() {
    await logout();
    setOpen(false);
    router.push("/");
  }

  return (
    <header className="sticky top-0 z-40 border-b border-line bg-canvas/95 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-6xl items-center gap-4 px-4">
        <Link href="/" className="flex items-center gap-2 font-semibold text-ink">
          <ChefHat aria-hidden className="h-6 w-6 text-brand" /> Bachelor Chef
        </Link>
        <nav aria-label="Main" className="hidden flex-1 items-center gap-1 md:flex">
          {links.map((l) => navLink(l.href, l.label))}
          {isAdmin && navLink("/admin", "Admin")}
        </nav>
        <div className="ml-auto hidden items-center gap-2 md:flex">
          {status === "authenticated" && user ? (
            <>
              <Link href="/profile" className="max-w-40 truncate rounded-lg px-3 py-2 text-sm hover:bg-sunken" data-testid="nav-profile">
                {user.fullName}
              </Link>
              <button type="button" onClick={handleLogout} className="inline-flex items-center gap-1 rounded-lg px-3 py-2 text-sm hover:bg-sunken" data-testid="logout">
                <LogOut aria-hidden className="h-4 w-4" /> Log out
              </button>
            </>
          ) : status === "anonymous" ? (
            <>
              <ButtonLink href="/login" variant="ghost" size="sm">Log in</ButtonLink>
              <ButtonLink href="/register" size="sm">Sign up</ButtonLink>
            </>
          ) : null}
        </div>
        <button type="button" className="ml-auto rounded-lg p-2 md:hidden" aria-expanded={open} aria-controls="mobile-nav"
          aria-label={open ? "Close menu" : "Open menu"} onClick={() => setOpen((o) => !o)}>
          {open ? <X aria-hidden className="h-6 w-6" /> : <Menu aria-hidden className="h-6 w-6" />}
        </button>
      </div>
      {open && (
        <nav id="mobile-nav" aria-label="Mobile" className="flex flex-col gap-1 border-t border-line px-4 py-3 md:hidden">
          {links.map((l) => navLink(l.href, l.label))}
          {isAdmin && (
            <Link href="/admin" onClick={() => setOpen(false)} className="flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium hover:bg-sunken">
              <Shield aria-hidden className="h-4 w-4" /> Admin
            </Link>
          )}
          {status === "authenticated" ? (
            <>
              {navLink("/profile", "Profile")}
              <button type="button" onClick={handleLogout} className="rounded-lg px-3 py-2 text-left text-sm hover:bg-sunken">Log out</button>
            </>
          ) : (
            <>
              {navLink("/login", "Log in")}
              {navLink("/register", "Sign up")}
            </>
          )}
        </nav>
      )}
    </header>
  );
}
