import type { Metadata } from "next";
import { Landing } from "@/components/landing/Landing";

export const metadata: Metadata = {
  title: "Nexus — one control plane for every app you ship",
  description:
    "Nexus is a self-hosted control plane that handles the things every app needs — identity, permissions, API keys, and shared services — so each project can focus on what makes it different.",
  openGraph: {
    title: "Nexus — one control plane for every app you ship",
    description:
      "A self-hosted control plane for identity, permissions, API keys, and shared services — shared once across everything you build.",
    type: "website",
  },
};

export default function Home() {
  return <Landing />;
}
