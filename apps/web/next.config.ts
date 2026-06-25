import type { NextConfig } from "next";

// NEXUS_ALLOWED_DEV_ORIGINS is also consumed by src/lib/api/routes.ts, which parses
// each value as a full origin (new URL(...)). allowedDevOrigins, however, expects bare
// hostnames — "https://host.ngrok-free.app" would never match. Normalize to hostname.
const allowedDevOrigins = (
  process.env.NEXUS_ALLOWED_DEV_ORIGINS ?? "127.0.0.1"
)
  .split(",")
  .map((value) => value.trim())
  .filter(Boolean)
  .map((value) => {
    try {
      return new URL(value).hostname;
    } catch {
      return value;
    }
  });

const nextConfig: NextConfig = {
  allowedDevOrigins,
};

export default nextConfig;
