"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  fetchCurrentAccount,
  fetchPanelSessions,
  revokeAllPanelSessions,
  revokePanelSession,
} from "./api";
import { queryKeys } from "@/lib/api/queryKeys";

/**
 * Cuenta actual del panel (`/me`). Devuelve `null` si no hay sesión (la API
 * resuelve el 401 a null dentro de `fetchCurrentAccount`, así que la query no
 * entra en error por sesión caducada).
 */
export function useCurrentAccount() {
  return useQuery({
    queryKey: queryKeys.me(),
    queryFn: () => fetchCurrentAccount(),
  });
}

/** Sesiones activas del panel. */
export function usePanelSessions() {
  return useQuery({
    queryKey: queryKeys.sessions(),
    queryFn: () => fetchPanelSessions(),
  });
}

/** Revoca una sesión concreta (los wrappers de session/api ya gestionan CSRF). */
export function useRevokePanelSession() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (publicSessionId: string) => revokePanelSession(publicSessionId),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.sessions() }),
  });
}

/** Revoca todas las sesiones salvo la actual. */
export function useRevokeAllPanelSessions() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => revokeAllPanelSessions(),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.sessions() }),
  });
}
