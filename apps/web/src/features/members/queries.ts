"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  fetchMembers,
  inviteMember,
  removeMember,
  transferOwnership,
  updateMemberRole,
  type InviteMemberPayload,
  type MemberRole,
} from "./api";
import { queryKeys } from "@/lib/api/queryKeys";
import { withCsrf } from "@/lib/api/csrf";

/** Listado de miembros de un proyecto (panel). */
export function useProjectMembers(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.members(projectId),
    enabled: !!projectId,
    queryFn: () => fetchMembers(projectId),
  });
}

/** Invita (o reactiva) una cuenta Nexus al proyecto con un rol. */
export function useInviteMember(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: InviteMemberPayload) =>
      withCsrf((token) => inviteMember(projectId, payload, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.members(projectId) }),
  });
}

/** Cambia el rol de un miembro (ADMIN↔MEMBER; OWNER solo vía transferencia). */
export function useUpdateMemberRole(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { memberId: string; role: MemberRole }) =>
      withCsrf((token) =>
        updateMemberRole(projectId, vars.memberId, vars.role, token),
      ),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.members(projectId) }),
  });
}

/** Elimina (soft, re-invitable) un miembro. */
export function useRemoveMember(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (memberId: string) =>
      withCsrf((token) => removeMember(projectId, memberId, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.members(projectId) }),
  });
}

/**
 * Transfiere la propiedad. Invalida además la ficha del proyecto: el actor deja
 * de ser OWNER y canManage/canDelete deben recalcularse en toda la UI.
 */
export function useTransferOwnership(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (memberId: string) =>
      withCsrf((token) => transferOwnership(projectId, memberId, token)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.projects.detail(projectId) });
      qc.invalidateQueries({ queryKey: queryKeys.projects.members(projectId) });
    },
  });
}
