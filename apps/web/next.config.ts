import type { NextConfig } from "next";

const allowedDevOrigins = (
  process.env.NEXUS_ALLOWED_DEV_ORIGINS ?? "127.0.0.1"
)
  .split(",")
  .map((origin) => origin.trim())
  .filter(Boolean);

const nextConfig: NextConfig = {
  allowedDevOrigins,
};

export default nextConfig;
