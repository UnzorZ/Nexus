"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  fetchInstanceSettings,
  fetchInstanceSmtp,
  fetchInstanceStatus,
  saveInstanceDefaultModules,
  saveInstanceHeartbeat,
  saveInstanceRegistration,
  saveInstanceSmtp,
  testInstanceSmtpConnection,
} from "./api";
import { queryKeys } from "@/lib/api/queryKeys";
import { withCsrf } from "@/lib/api/csrf";

/** SMTP de instancia (relay del operador). */
export function useInstanceSmtpSettings() {
  return useQuery({
    queryKey: queryKeys.instance.smtp(),
    queryFn: () => fetchInstanceSmtp(),
  });
}

export function useSaveInstanceSmtpSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: {
      host: string;
      port: number;
      username: string;
      from: string;
      password: string;
      tlsMode: "PUBLIC" | "PINNED";
      trustedCaPem?: string;
    }) => withCsrf((token) => saveInstanceSmtp(vars, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.instance.smtp() }),
  });
}

/** Comprueba la conexión del SMTP de instancia sin enviar correo. */
export function useTestInstanceSmtpConnection() {
  return useMutation({
    mutationFn: () => withCsrf((token) => testInstanceSmtpConnection(token)),
  });
}

/** Status de sólo lectura de la configuración operativa de la instancia. */
export function useInstanceStatus() {
  return useQuery({
    queryKey: queryKeys.instance.status(),
    queryFn: () => fetchInstanceStatus(),
  });
}

/** Configuración writeable de instancia (registro, módulos, heartbeat). */
export function useInstanceSettings() {
  return useQuery({
    queryKey: queryKeys.instance.settings(),
    queryFn: () => fetchInstanceSettings(),
  });
}

export function useSaveInstanceRegistration() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (open: boolean) => withCsrf((token) => saveInstanceRegistration(open, token)),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.instance.settings() }),
  });
}

/** modules=null resetea a los defaults del catálogo. */
export function useSaveInstanceDefaultModules() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (modules: string[] | null) =>
      withCsrf((token) => saveInstanceDefaultModules(modules, token)),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.instance.settings() }),
  });
}

/** Ambos null = clear (vuelve al env). */
export function useSaveInstanceHeartbeat() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { intervalSeconds: number | null; timeoutSeconds: number | null }) =>
      withCsrf((token) => saveInstanceHeartbeat(vars.intervalSeconds, vars.timeoutSeconds, token)),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.instance.settings() }),
  });
}
