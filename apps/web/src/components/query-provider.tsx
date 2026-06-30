"use client";

import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";

/**
 * Cliente singleton por navegador. En el server (render inicial del HTML) se
 * crea uno nuevo en cada llamada —sin prefetch no hay caché que conservar—; en
 * el navegador se reutiliza para no perder la caché entre navegaciones y
 * refrescos de componente. Patrón recomendado por la guía de TanStack para el
 * App Router de Next (evita resetear la caché en cada render y los desajustes
 * de hidratación).
 */
let browserQueryClient: QueryClient | undefined;

function makeQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // Los datos son server-authoritative vía invalidateQueries; 30s evita
        // refetchs redundantes al saltar entre pestañas rápidamente.
        staleTime: 30_000,
        retry: 1,
        refetchOnWindowFocus: true,
      },
    },
  });
}

function getQueryClient(): QueryClient {
  if (typeof window === "undefined") {
    return makeQueryClient();
  }
  if (!browserQueryClient) {
    browserQueryClient = makeQueryClient();
  }
  return browserQueryClient;
}

export function QueryProvider({ children }: { children: ReactNode }) {
  const queryClient = getQueryClient();
  return (
    <QueryClientProvider client={queryClient}>
      {children}
      {/* No-op en producción; panel flotante solo visible en dev. */}
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  );
}
