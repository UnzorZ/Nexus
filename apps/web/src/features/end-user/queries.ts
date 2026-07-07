"use client";

import { useQuery } from "@tanstack/react-query";
import { fetchEndUserMe } from "./api";
import { queryKeys } from "@/lib/api/queryKeys";

/**
 * Usuario final autenticado de un proyecto (`/api/p/{slug}/me`). Devuelve `null` si no
 * hay sesión (el 401 se resuelve a null dentro de `fetchEndUserMe`).
 */
export function useEndUserMe(projectSlug: string) {
  return useQuery({
    queryKey: queryKeys.endUser.me(projectSlug),
    queryFn: () => fetchEndUserMe(projectSlug),
  });
}
