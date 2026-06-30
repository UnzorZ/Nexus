"use client";

import { useCallback, useEffect, useState } from "react";
import { NexusApiError } from "@/lib/api/client";
import { ensureCsrfToken } from "@/lib/api/csrf";
import { parseFieldErrors } from "@/features/projects/api";
import {
  createPermission,
  deletePermission,
  fetchPermissions,
  updatePermission,
  type Permission,
} from "./api";

export type UseProjectPermissionsResult = {
  permissions: Permission[] | null;
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
    permissionId: string,
    body: { label: string; description: string | null },
  ) => Promise<boolean>;
  remove: (permissionId: string) => Promise<boolean>;
  refresh: () => void;
};

type MutationOutcome = {
  actionError: string;
  fieldErrors: Record<string, string>;
};

function classify(err: unknown, fallback: string): MutationOutcome {
  if (err instanceof NexusApiError) {
    if (err.status === 400 && err.code === "validation_error") {
      const detail =
        (err.data as { detail?: string } | null)?.detail ?? "";
      return {
        actionError: "Revisa los campos marcados.",
        fieldErrors: parseFieldErrors(detail),
      };
    }
    if (err.status === 403 && err.code === "permission_denied") {
      return {
        actionError:
          "You don't have permission to manage permissions for this project.",
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
      return { actionError: "This permission no longer exists.", fieldErrors: {} };
    }
    return { actionError: err.message, fieldErrors: {} };
  }
  return { actionError: fallback, fieldErrors: {} };
}

export function useProjectPermissions(
  projectId: string,
): UseProjectPermissionsResult {
  const [permissions, setPermissions] = useState<Permission[] | null>(null);
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
      setPermissions(await fetchPermissions(projectId));
    } catch (err) {
      setError(
        err instanceof NexusApiError
          ? err.message
          : "No se pudieron cargar los permisos.",
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
        const created = await createPermission(projectId, body, token);
        setPermissions((current) => [...(current ?? []), created]);
        return true;
      } catch (err) {
        const outcome = classify(err, "No se pudo crear el permiso.");
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
      permissionId: string,
      body: { label: string; description: string | null },
    ): Promise<boolean> => {
      if (!projectId) return false;
      setBusy(true);
      setActionError(null);
      setFieldErrors({});
      try {
        const token = await ensureCsrfToken();
        const updated = await updatePermission(
          projectId,
          permissionId,
          body,
          token,
        );
        setPermissions((current) =>
          (current ?? []).map((p) => (p.id === permissionId ? updated : p)),
        );
        return true;
      } catch (err) {
        const outcome = classify(err, "No se pudo actualizar el permiso.");
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
    async (permissionId: string): Promise<boolean> => {
      if (!projectId) return false;
      setBusy(true);
      setActionError(null);
      setFieldErrors({});
      try {
        const token = await ensureCsrfToken();
        await deletePermission(projectId, permissionId, token);
        setPermissions((current) =>
          (current ?? []).filter((p) => p.id !== permissionId),
        );
        return true;
      } catch (err) {
        const outcome = classify(err, "No se pudo eliminar el permiso.");
        setActionError(outcome.actionError);
        return false;
      } finally {
        setBusy(false);
      }
    },
    [projectId],
  );

  return {
    permissions,
    loading,
    error,
    actionError,
    fieldErrors,
    busy,
    create,
    update,
    remove,
    refresh: load,
  };
}
