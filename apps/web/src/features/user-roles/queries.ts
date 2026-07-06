"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchUserRoles, setUserRoles } from "./api";
import { queryKeys } from "@/lib/api/queryKeys";
import { withCsrf } from "@/lib/api/csrf";

/** Roles asignados a un usuario de proyecto. Se habilita al abrir el diálogo. */
export function useUserRoles(projectId: string, userId: string) {
  return useQuery({
    queryKey: queryKeys.projects.userRoles(projectId, userId),
    enabled: Boolean(projectId) && Boolean(userId),
    queryFn: () => fetchUserRoles(projectId, userId),
  });
}

/** Reemplazo completo (PUT) de los roles del usuario (PUT semántica). */
export function useSetUserRoles(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { userId: string; roleIds: string[] }) =>
      withCsrf((token) => setUserRoles(projectId, vars.userId, vars.roleIds, token)),
    onSuccess: (_data, vars) =>
      qc.invalidateQueries({
        queryKey: queryKeys.projects.userRoles(projectId, vars.userId),
      }),
  });
}
