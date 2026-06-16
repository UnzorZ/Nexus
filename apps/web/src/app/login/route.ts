import { redirect } from "next/navigation";
import { buildPanelLoginUrl } from "@/lib/api/routes";

export function GET(request: Request) {
  redirect(buildPanelLoginUrl("/dashboard", { request }));
}
