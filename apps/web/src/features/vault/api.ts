import { apiClient } from "@/lib/api/client";
import { CSRF_HEADER_NAME } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";

export type VaultCipher = "AES_256_GCM" | "CHACHA20_POLY1305";

export const VAULT_CIPHERS: VaultCipher[] = ["AES_256_GCM", "CHACHA20_POLY1305"];

export type Secret = {
  id: string;
  key: string;
  cipher: VaultCipher;
  createdAt: string;
  updatedAt: string;
  lastRotatedAt: string | null;
};

/** El valor plano nunca se devuelve desde el panel. */
export async function fetchSecrets(projectId: string): Promise<Secret[]> {
  return apiClient.get<Secret[]>(
    apiRoutes.panel.projects.vault.secretsRoot(projectId),
    { redirect: "manual", errorMessage: "No se pudieron cargar los secretos." },
  );
}

export async function createSecret(
  projectId: string,
  key: string,
  value: string,
  cipher: VaultCipher,
  csrfToken: string,
): Promise<Secret> {
  return apiClient.post<Secret>(
    apiRoutes.panel.projects.vault.secretByKey(projectId, key),
    { value, cipher },
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo crear el secreto.",
    },
  );
}

export async function rotateSecret(
  projectId: string,
  key: string,
  value: string,
  cipher: VaultCipher,
  csrfToken: string,
): Promise<Secret> {
  return apiClient.patch<Secret>(
    apiRoutes.panel.projects.vault.secretByKey(projectId, key),
    { value, cipher },
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo rotar el secreto.",
    },
  );
}

export type SecretValue = { key: string; value: string };

/** Revela el valor descifrado de un secreto desde el panel (Manage, auditado). */
export async function revealSecretValue(
  projectId: string,
  key: string,
  csrfToken: string,
): Promise<SecretValue> {
  return apiClient.get<SecretValue>(
    apiRoutes.panel.projects.vault.secretValue(projectId, key),
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo revelar el secreto.",
    },
  );
}

export type VaultEncryptionInfo = {
  projectId: string;
  secretCount: number;
  defaultCipher: VaultCipher;
  cipherOptions: VaultCipher[];
  masterKeyOverridden: boolean;
};

export async function fetchVaultEncryption(
  projectId: string,
): Promise<VaultEncryptionInfo> {
  return apiClient.get<VaultEncryptionInfo>(
    apiRoutes.panel.projects.vault.encryption(projectId),
    { redirect: "manual", errorMessage: "No se pudo cargar la configuración de cifrado." },
  );
}

export type MasterKeyReveal = { masterKey: string; overridden: boolean };

export async function revealMasterKey(
  projectId: string,
  csrfToken: string,
): Promise<MasterKeyReveal> {
  return apiClient.get<MasterKeyReveal>(
    apiRoutes.panel.projects.vault.masterKey(projectId),
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo revelar la master key.",
    },
  );
}

export async function rotateMasterKey(
  projectId: string,
  masterKey: string,
  csrfToken: string,
): Promise<VaultEncryptionInfo> {
  return apiClient.post<VaultEncryptionInfo>(
    apiRoutes.panel.projects.vault.masterKey(projectId),
    { masterKey },
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo rotar la master key.",
    },
  );
}

export async function deleteSecret(
  projectId: string,
  key: string,
  csrfToken: string,
): Promise<void> {
  await apiClient.delete<void>(
    apiRoutes.panel.projects.vault.secretByKey(projectId, key),
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo eliminar el secreto.",
    },
  );
}
