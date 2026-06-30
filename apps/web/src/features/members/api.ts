import { apiClient } from "@/lib/api/client";
import { CSRF_HEADER_NAME } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";

export type MemberRole = "OWNER" | "ADMIN" | "MEMBER";
export type MemberStatus = "ACTIVE" | "INVITED" | "SUSPENDED" | "REVOKED";

export type Member = {
  id: string;
  accountId: string;
  email: string;
  displayName: string;
  role: MemberRole;
  status: MemberStatus;
  mfaEnabled: boolean;
  lastActiveAt: string | null;
  createdAt: string;
};

export type InviteMemberPayload = { email: string; role: MemberRole };

export async function fetchMembers(projectId: string): Promise<Member[]> {
  return apiClient.get<Member[]>(apiRoutes.panel.projects.members.root(projectId), {
    redirect: "manual",
    errorMessage: "No se pudieron cargar los miembros.",
  });
}

export async function inviteMember(
  projectId: string,
  payload: InviteMemberPayload,
  csrfToken: string,
): Promise<Member> {
  return apiClient.post<Member>(
    apiRoutes.panel.projects.members.root(projectId),
    payload,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo invitar al miembro.",
    },
  );
}

export async function updateMemberRole(
  projectId: string,
  memberId: string,
  role: MemberRole,
  csrfToken: string,
): Promise<Member> {
  return apiClient.patch<Member>(
    apiRoutes.panel.projects.members.byId(projectId, memberId),
    { role },
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo cambiar el rol del miembro.",
    },
  );
}

export async function removeMember(
  projectId: string,
  memberId: string,
  csrfToken: string,
): Promise<void> {
  await apiClient.delete<null>(
    apiRoutes.panel.projects.members.byId(projectId, memberId),
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo eliminar al miembro.",
    },
  );
}

export async function transferOwnership(
  projectId: string,
  memberId: string,
  csrfToken: string,
): Promise<void> {
  await apiClient.post<null>(
    apiRoutes.panel.projects.members.transferOwnership(projectId, memberId),
    null,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo transferir la propiedad.",
    },
  );
}
