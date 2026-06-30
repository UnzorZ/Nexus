"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createRole,
  deleteRole,
  fetchRoles,
  setRolePermissions,
  updateRole,
  type Role,
} from "./api";
import { queryKeys } from "@/lib/api/queryKeys";
import { withCsrf } from "@/lib/api/csrf";

/** Listado de roles de un proyecto (panel). */
export function useProjectRoles(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.roles(projectId),
    enabled: !!projectId,
    queryFn: () => fetchRoles(projectId),
  });
}

/** Crea un rol (clave estable + etiqueta + descripción). */
export function useCreateRole(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: {
      key: string;
      label: string;
      description: string | null;
    }) => withCsrf((token) => createRole(projectId, vars, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.roles(projectId) }),
  });
}

/** Actualiza etiqueta/descripción de un rol (la clave es inmutable). */
export function useUpdateRole(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: {
      roleId: string;
      body: { label: string; description: string | null };
    }) => withCsrf((token) => updateRole(projectId, vars.roleId, vars.body, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.roles(projectId) }),
  });
}

/** Elimina un rol (no system). */
export function useDeleteRole(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (roleId: string) =>
      withCsrf((token) => deleteRole(projectId, roleId, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.roles(projectId) }),
  });
}

/** Reemplazo completo (PUT) de las claves de permiso asignadas al rol. */
export function useSetRolePermissions(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { roleId: string; permissionKeys: string[] }) =>
      withCsrf((token) =>
        setRolePermissions(projectId, vars.roleId, vars.permissionKeys, token),
      ),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.roles(projectId) }),
  });
}

/** Tipo reexportado para comodidad de las páginas. */
export type { Role };
