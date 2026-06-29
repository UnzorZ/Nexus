"use client";

import { useCallback, useEffect, useState } from "react";
import { NexusApiError } from "@/lib/api/client";
import { ensureCsrfToken } from "@/lib/api/csrf";
import { parseFieldErrors } from "@/features/projects/api";
import {
  createRole,
  deleteRole,
  fetchRoles,
  setRolePermissions,
  updateRole,
  type Role,
} from "./api";

export type UseProjectRolesResult = {
  roles: Role[] | null;
  loading: boolean;
  error: string | null;
  actionError: string | null;
  fieldErrors: Record<string, string>;
  busy: boolean;
  create: (body: {
    key: string;
    label: string;
    description: string | null;
  }) => Promise<boolean>;
  update: (
    roleId: string,
    body: { label: string; description: string | null },
  ) => Promise<boolean>;
  remove: (roleId: string) => Promise<boolean>;
  setPermissions: (roleId: string, permissionKeys: string[]) => Promise<boolean>;
  refresh: () => void;
};

type MutationOutcome = {
  actionError: string;
  fieldErrors: Record<string, string>;
};

function classify(err: unknown, fallback: string): MutationOutcome {
  if (err instanceof NexusApiError) {
    if (err.status === 400 && err.code === "validation_error") {
      const detail = (err.data as { detail?: string } | null)?.detail ?? "";
      return {
        actionError: "Revisa los campos marcados.",
        fieldErrors: parseFieldErrors(detail),
      };
    }
    if (err.status === 403 && err.code === "permission_denied") {
      return {
        actionError: "You don't have permission to manage roles for this project.",
        fieldErrors: {},
      };
    }
    if (err.status === 403) {
      return {
        actionError: "Your session expired. Reload the page and try again.",
        fieldErrors: {},
      };
    }
    if (err.status === 409) {
      return { actionError: err.message, fieldErrors: {} };
    }
    if (err.status === 404) {
      return { actionError: "This role no longer exists.", fieldErrors: {} };
    }
    return { actionError: err.message, fieldErrors: {} };
  }
  return { actionError: fallback, fieldErrors: {} };
}

export function useProjectRoles(projectId: string): UseProjectRolesResult {
  const [roles, setRoles] = useState<Role[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    if (!projectId) {
      setLoading(false);
      return;
    }
    try {
      setLoading(true);
      setError(null);
      setRoles(await fetchRoles(projectId));
    } catch (err) {
      setError(
        err instanceof NexusApiError
          ? err.message
          : "No se pudieron cargar los roles.",
      );
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    load();
  }, [load]);

  const create = useCallback(
    async (body: {
      key: string;
      label: string;
      description: string | null;
    }): Promise<boolean> => {
      if (!projectId) return false;
      setBusy(true);
      setActionError(null);
      setFieldErrors({});
      try {
        const token = await ensureCsrfToken();
        const created = await createRole(projectId, body, token);
        setRoles((current) => [...(current ?? []), created]);
        return true;
      } catch (err) {
        const outcome = classify(err, "No se pudo crear el rol.");
        setActionError(outcome.actionError);
        setFieldErrors(outcome.fieldErrors);
        return false;
      } finally {
        setBusy(false);
      }
    },
    [projectId],
  );

  const update = useCallback(
    async (
      roleId: string,
      body: { label: string; description: string | null },
    ): Promise<boolean> => {
      if (!projectId) return false;
      setBusy(true);
      setActionError(null);
      setFieldErrors({});
      try {
        const token = await ensureCsrfToken();
        const updated = await updateRole(projectId, roleId, body, token);
        setRoles((current) =>
          (current ?? []).map((r) => (r.id === roleId ? updated : r)),
        );
        return true;
      } catch (err) {
        const outcome = classify(err, "No se pudo actualizar el rol.");
        setActionError(outcome.actionError);
        setFieldErrors(outcome.fieldErrors);
        return false;
      } finally {
        setBusy(false);
      }
    },
    [projectId],
  );

  const remove = useCallback(
    async (roleId: string): Promise<boolean> => {
      if (!projectId) return false;
      setBusy(true);
      setActionError(null);
      setFieldErrors({});
      try {
        const token = await ensureCsrfToken();
        await deleteRole(projectId, roleId, token);
        setRoles((current) => (current ?? []).filter((r) => r.id !== roleId));
        return true;
      } catch (err) {
        const outcome = classify(err, "No se pudo eliminar el rol.");
        setActionError(outcome.actionError);
        return false;
      } finally {
        setBusy(false);
      }
    },
    [projectId],
  );

  const setPermissions = useCallback(
    async (roleId: string, permissionKeys: string[]): Promise<boolean> => {
      if (!projectId) return false;
      setBusy(true);
      setActionError(null);
      setFieldErrors({});
      try {
        const token = await ensureCsrfToken();
        const updated = await setRolePermissions(
          projectId,
          roleId,
          permissionKeys,
          token,
        );
        setRoles((current) =>
          (current ?? []).map((r) => (r.id === roleId ? updated : r)),
        );
        return true;
      } catch (err) {
        const outcome = classify(err, "No se pudieron actualizar los permisos del rol.");
        setActionError(outcome.actionError);
        setFieldErrors(outcome.fieldErrors);
        return false;
      } finally {
        setBusy(false);
      }
    },
    [projectId],
  );

  return {
    roles,
    loading,
    error,
    actionError,
    fieldErrors,
    busy,
    create,
    update,
    remove,
    setPermissions,
    refresh: load,
  };
}
