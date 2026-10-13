import type { Metadata } from "next";
import { Geist, Geist_Mono, Noto_Sans_Bengali } from "next/font/google";
import { SiteHeader } from "@/components/layout/site-header";
import { Providers } from "./providers";
import "./globals.css";

const geistSans = Geist({ variable: "--font-geist-sans", subsets: ["latin"] });
const geistMono = Geist_Mono({ variable: "--font-geist-mono", subsets: ["latin"] });
// Fallback for the Taka sign (৳), which Geist does not include; only downloaded for Bengali code points.
const bengali = Noto_Sans_Bengali({ variable: "--font-bengali", subsets: ["bengali"], weight: ["400", "600"] });

export const metadata: Metadata = {
  title: { default: "Bachelor Chef", template: "%s · Bachelor Chef" },
  description: "Cook with what you have: quick, budget-friendly recipes for students and busy people in Bangladesh.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className={`${geistSans.variable} ${geistMono.variable} ${bengali.variable} h-full antialiased`}>
      <body className="flex min-h-full flex-col font-sans">
        <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-surface focus:px-4 focus:py-2">
          Skip to content
        </a>
        <Providers>
          <SiteHeader />
          <main id="main" className="flex-1">{children}</main>
          <footer className="border-t border-line py-6 text-center text-xs text-muted">
            Bachelor Chef · Costs are estimates in BDT, not live market prices.
          </footer>
        </Providers>
      </body>
    </html>
  );
}
