import type { Metadata } from "next";
import { LandingV2 } from "@/components/landing-v2/LandingV2";

export const metadata: Metadata = {
  title: "Nexus — the shared foundation for everything you build",
  description:
    "An alternate take on the Nexus control plane: identity, permissions, API keys, and live health — the pieces every app needs, built once and shared across all of them.",
  robots: { index: false, follow: false },
};

export default function V2Page() {
  return <LandingV2 />;
}
