"use client";

import { useCallback, useEffect, useState } from "react";
import {
  fetchMembers,
  inviteMember,
  removeMember,
  transferOwnership,
  updateMemberRole,
  type InviteMemberPayload,
  type Member,
} from "./api";
import { parseFieldErrors } from "@/features/projects/api";
import { NexusApiError } from "@/lib/api/client";
import { ensureCsrfToken } from "@/lib/api/csrf";

export type UseProjectMembersResult = {
  members: Member[] | null;
  loading: boolean;
  error: string | null;
  actionError: string | null;
  inviteError: string | null;
  inviteFieldErrors: Record<string, string>;
  busy: boolean;
  invite: (payload: InviteMemberPayload) => Promise<boolean>;
  changeRole: (memberId: string, role: Member["role"]) => Promise<void>;
  remove: (memberId: string) => Promise<void>;
  transfer: (memberId: string) => Promise<boolean>;
  refresh: () => void;
};

/** Maps a mutation error to a user-facing message using the established ladder. */
function classifyError(err: unknown): string {
  if (err instanceof NexusApiError && err.status === 403 && err.code === "permission_denied") {
    return "You don't have permission to manage members.";
  }
  if (err instanceof NexusApiError && err.status === 403) {
    return "Your session expired. Reload the page and try again.";
  }
  if (err instanceof NexusApiError && err.status === 404) {
    return "This member no longer exists.";
  }
  if (err instanceof NexusApiError && err.status === 409 && err.code === "last_owner") {
    return "The project must keep at least one active owner.";
  }
  if (err instanceof NexusApiError && err.status === 409 && err.code === "already_owner") {
    return "That member is already the owner.";
  }
  return err instanceof NexusApiError ? err.message : "Something went wrong.";
}

export function useProjectMembers(projectId: string): UseProjectMembersResult {
  const [members, setMembers] = useState<Member[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [inviteError, setInviteError] = useState<string | null>(null);
  const [inviteFieldErrors, setInviteFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    if (!projectId) {
      setLoading(false);
      return;
    }
    try {
      setLoading(true);
      setError(null);
      const data = await fetchMembers(projectId);
      setMembers(data);
    } catch (err) {
      setError(
        err instanceof NexusApiError ? err.message : "No se pudieron cargar los miembros.",
      );
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    load();
  }, [load]);

  // Silent reconcile after a successful mutation: updates the list without
  // toggling the loading skeleton.
  const reconcile = useCallback(async () => {
    try {
      const data = await fetchMembers(projectId);
      setMembers(data);
    } catch {
      /* the mutation itself succeeded; ignore a stale re-read */
    }
  }, [projectId]);

  const invite = useCallback(
    async (payload: InviteMemberPayload) => {
      setBusy(true);
      setInviteError(null);
      setInviteFieldErrors({});
      try {
        const token = await ensureCsrfToken();
        await inviteMember(projectId, payload, token);
        await reconcile();
        return true;
      } catch (err) {
        if (err instanceof NexusApiError && err.status === 400 && err.code === "validation_error") {
          setInviteFieldErrors(parseFieldErrors(err.message));
        } else {
          setInviteError(classifyError(err));
        }
        return false;
      } finally {
        setBusy(false);
      }
    },
    [projectId, reconcile],
  );

  const changeRole = useCallback(
    async (memberId: string, role: Member["role"]) => {
      setBusy(true);
      setActionError(null);
      try {
        const token = await ensureCsrfToken();
        await updateMemberRole(projectId, memberId, role, token);
        await reconcile();
      } catch (err) {
        setActionError(classifyError(err));
      } finally {
        setBusy(false);
      }
    },
    [projectId, reconcile],
  );

  const remove = useCallback(
    async (memberId: string) => {
      setBusy(true);
      setActionError(null);
      try {
        const token = await ensureCsrfToken();
        await removeMember(projectId, memberId, token);
        await reconcile();
      } catch (err) {
        setActionError(classifyError(err));
      } finally {
        setBusy(false);
      }
    },
    [projectId, reconcile],
  );

  const transfer = useCallback(
    async (memberId: string) => {
      setBusy(true);
      setActionError(null);
      try {
        const token = await ensureCsrfToken();
        await transferOwnership(projectId, memberId, token);
        await reconcile();
        return true;
      } catch (err) {
        setActionError(classifyError(err));
        return false;
      } finally {
        setBusy(false);
      }
    },
    [projectId, reconcile],
  );

  return {
    members,
    loading,
    error,
    actionError,
    inviteError,
    inviteFieldErrors,
    busy,
    invite,
    changeRole,
    remove,
    transfer,
    refresh: load,
  };
}
