import type { NextConfig } from "next";

/**
 * By default the browser calls the API on the same origin (/api/v1/...) and Next.js proxies it to
 * the Spring Boot backend. This keeps the HttpOnly refresh cookie first-party in every browser.
 * API_PROXY_TARGET is server-side only (never exposed to the client).
 */
const apiTarget = (process.env.API_PROXY_TARGET || "http://localhost:8080").replace(/\/$/, "");

const nextConfig: NextConfig = {
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  poweredByHeader: false,
  async rewrites() {
    return [{ source: "/api/v1/:path*", destination: `${apiTarget}/api/v1/:path*` }];
  },
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "X-Frame-Options", value: "DENY" },
        ],
      },
    ];
  },
};

export default nextConfig;
