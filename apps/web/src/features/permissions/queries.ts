"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createPermission,
  deletePermission,
  fetchPermissions,
  updatePermission,
} from "./api";
import { queryKeys } from "@/lib/api/queryKeys";
import { withCsrf } from "@/lib/api/csrf";

/** Listado de permisos declarados de un proyecto (panel). */
export function useProjectPermissions(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.permissions(projectId),
    enabled: !!projectId,
    queryFn: () => fetchPermissions(projectId),
  });
}

/** Declara un permiso nuevo (clave, etiqueta, descripción). */
export function useCreatePermission(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: {
      key: string;
      label: string;
      description: string | null;
    }) => withCsrf((token) => createPermission(projectId, vars, token)),
    onSuccess: () =>
      qc.invalidateQueries({
        queryKey: queryKeys.projects.permissions(projectId),
      }),
  });
}

/** Actualiza la etiqueta y descripción de un permiso (la clave es inmutable). */
export function useUpdatePermission(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: {
      permissionId: string;
      label: string;
      description: string | null;
    }) =>
      withCsrf((token) =>
        updatePermission(
          projectId,
          vars.permissionId,
          { label: vars.label, description: vars.description },
          token,
        ),
      ),
    onSuccess: () =>
      qc.invalidateQueries({
        queryKey: queryKeys.projects.permissions(projectId),
      }),
  });
}

/** Elimina un permiso del catálogo (los roles conservan sus grants por clave). */
export function useDeletePermission(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (permissionId: string) =>
      withCsrf((token) => deletePermission(projectId, permissionId, token)),
    onSuccess: () =>
      qc.invalidateQueries({
        queryKey: queryKeys.projects.permissions(projectId),
      }),
  });
}
