"use client";

import { useCallback, useEffect, useState } from "react";
import { NexusApiError } from "@/lib/api/client";
import { ensureCsrfToken } from "@/lib/api/csrf";
import { parseFieldErrors } from "@/features/projects/api";
import {
  createApiKey,
  deleteApiKey,
  fetchApiKeys,
  rotateApiKey,
  updateApiKey,
  type ApiKeyCreated,
  type ApiKeyStatus,
  type ApiKeySummary,
} from "./api";

export type UseProjectApiKeysResult = {
  keys: ApiKeySummary[] | null;
  loading: boolean;
  error: string | null;
  actionError: string | null;
  fieldErrors: Record<string, string>;
  busy: boolean;
  create: (body: {
    name: string;
    scopes: string[];
    expiresAt: string | null;
  }) => Promise<ApiKeyCreated | null>;
  update: (
    keyId: string,
    body: { name: string; status: ApiKeyStatus; expiresAt: string | null },
  ) => Promise<boolean>;
  rotate: (keyId: string) => Promise<ApiKeyCreated | null>;
  remove: (keyId: string) => Promise<boolean>;
  refresh: () => void;
};

function classify(
  err: unknown,
  fallback: string,
): { actionError: string; fieldErrors: Record<string, string> } {
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
        actionError:
          "You don't have permission to manage API keys for this project.",
        fieldErrors: {},
      };
    }
    if (err.status === 403) {
      return {
        actionError: "Your session expired. Reload the page and try again.",
        fieldErrors: {},
      };
    }
    if (err.status === 404) {
      return { actionError: "This API key no longer exists.", fieldErrors: {} };
    }
    return { actionError: err.message, fieldErrors: {} };
  }
  return { actionError: fallback, fieldErrors: {} };
}

export function useProjectApiKeys(projectId: string): UseProjectApiKeysResult {
  const [keys, setKeys] = useState<ApiKeySummary[] | null>(null);
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
      setKeys(await fetchApiKeys(projectId));
    } catch (err) {
      setError(
        err instanceof NexusApiError
          ? err.message
          : "No se pudieron cargar las API keys.",
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
      name: string;
      scopes: string[];
      expiresAt: string | null;
    }): Promise<ApiKeyCreated | null> => {
      if (!projectId) return null;
      setBusy(true);
      setActionError(null);
      setFieldErrors({});
      try {
        const token = await ensureCsrfToken();
        const created = await createApiKey(projectId, body, token);
        setKeys((current) => [...(current ?? []), created.summary]);
        return created;
      } catch (err) {
        const outcome = classify(err, "No se pudo crear la API key.");
        setActionError(outcome.actionError);
        setFieldErrors(outcome.fieldErrors);
        return null;
      } finally {
        setBusy(false);
      }
    },
    [projectId],
  );

  const update = useCallback(
    async (
      keyId: string,
      body: { name: string; status: ApiKeyStatus; expiresAt: string | null },
    ): Promise<boolean> => {
      if (!projectId) return false;
      setBusy(true);
      setActionError(null);
      setFieldErrors({});
      try {
        const token = await ensureCsrfToken();
        const updated = await updateApiKey(projectId, keyId, body, token);
        setKeys((current) =>
          (current ?? []).map((k) => (k.id === keyId ? updated : k)),
        );
        return true;
      } catch (err) {
        const outcome = classify(err, "No se pudo actualizar la API key.");
        setActionError(outcome.actionError);
        setFieldErrors(outcome.fieldErrors);
        return false;
      } finally {
        setBusy(false);
      }
    },
    [projectId],
  );

  const rotate = useCallback(
    async (keyId: string): Promise<ApiKeyCreated | null> => {
      if (!projectId) return null;
      setBusy(true);
      setActionError(null);
      setFieldErrors({});
      try {
        const token = await ensureCsrfToken();
        const created = await rotateApiKey(projectId, keyId, token);
        setKeys((current) =>
          (current ?? [])
            .map((k) =>
              k.id === keyId ? { ...k, status: "DISABLED" as const } : k,
            )
            .concat(created.summary),
        );
        return created;
      } catch (err) {
        const outcome = classify(err, "No se pudo rotar la API key.");
        setActionError(outcome.actionError);
        return null;
      } finally {
        setBusy(false);
      }
    },
    [projectId],
  );

  const remove = useCallback(
    async (keyId: string): Promise<boolean> => {
      if (!projectId) return false;
      setBusy(true);
      setActionError(null);
      setFieldErrors({});
      try {
        const token = await ensureCsrfToken();
        await deleteApiKey(projectId, keyId, token);
        setKeys((current) => (current ?? []).filter((k) => k.id !== keyId));
        return true;
      } catch (err) {
        const outcome = classify(err, "No se pudo eliminar la API key.");
        setActionError(outcome.actionError);
        return false;
      } finally {
        setBusy(false);
      }
    },
    [projectId],
  );

  return {
    keys,
    loading,
    error,
    actionError,
    fieldErrors,
    busy,
    create,
    update,
    rotate,
    remove,
    refresh: load,
  };
}
