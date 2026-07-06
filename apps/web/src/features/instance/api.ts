import { apiClient } from "@/lib/api/client";
import { CSRF_HEADER_NAME } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";

/**
 * SMTP de instancia (relay del operador). Mismo shape que el SMTP por proyecto
 * salvo que no lleva projectId (la fila es singleton a nivel instancia).
 */
export type InstanceSmtpSettings = {
  host: string | null;
  port: number;
  username: string | null;
  from: string | null;
  passwordConfigured: boolean;
  tlsMode: "PUBLIC" | "PINNED";
  trustedCaConfigured: boolean;
  updatedAt: string | null;
};

export type SmtpConnectionCheck = { ok: boolean; message: string };

/** Status de sólo lectura de la configuración operativa de la instancia. */
export type InstanceStatus = {
  registration: { policy: string; note: string };
  session: {
    timeout: string;
    cookieSameSite: string;
    cookieSecure: boolean;
    cookieHttpOnly: boolean;
  };
  vaultMasterKey: { status: "configured" | "dev-default" | string };
  jwtKeystore: {
    status: "persistent" | "ephemeral" | string;
    keystoreLocation: string | null;
  };
  frontendBaseUrl: string;
  allowedOrigins: string;
  heartbeat: { intervalSeconds: number; timeoutSeconds: number };
  modules: { key: string; defaultEnabled: boolean }[];
  instance: {
    appName: string;
    version: string | null;
    frontendBaseUrl: string;
  };
};

export async function fetchInstanceSmtp(): Promise<InstanceSmtpSettings> {
  return apiClient.get<InstanceSmtpSettings>(apiRoutes.panel.instance.smtp, {
    redirect: "manual",
    errorMessage: "No se pudo cargar el SMTP de instancia.",
  });
}

export async function saveInstanceSmtp(
  body: {
    host: string;
    port: number;
    username: string;
    from: string;
    password: string;
    tlsMode: "PUBLIC" | "PINNED";
    trustedCaPem?: string;
  },
  csrfToken: string,
): Promise<InstanceSmtpSettings> {
  return apiClient.put<InstanceSmtpSettings>(
    apiRoutes.panel.instance.smtp,
    body,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo guardar el SMTP de instancia.",
    },
  );
}

/** Comprueba la conexión del SMTP de instancia (STARTTLS + AUTH) sin enviar correo. */
export async function testInstanceSmtpConnection(
  csrfToken: string,
): Promise<SmtpConnectionCheck> {
  return apiClient.post<SmtpConnectionCheck>(
    apiRoutes.panel.instance.smtpTestConnection,
    {},
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo comprobar la conexión SMTP.",
    },
  );
}

export async function fetchInstanceStatus(): Promise<InstanceStatus> {
  return apiClient.get<InstanceStatus>(apiRoutes.panel.instance.status, {
    redirect: "manual",
    errorMessage: "No se pudo cargar el estado de la instancia.",
  });
}

/** Configuración writeable de instancia (registro, módulos por defecto, heartbeat). */
export type InstanceSettings = {
  registrationOpen: boolean;
  /** null = usar los defaults del catálogo; array = set activo (vacío = ninguno). */
  defaultModules: string[] | null;
  heartbeat: { intervalSeconds: number | null; timeoutSeconds: number | null };
  updatedAt: string | null;
};

export async function fetchInstanceSettings(): Promise<InstanceSettings> {
  return apiClient.get<InstanceSettings>(apiRoutes.panel.instance.settings, {
    redirect: "manual",
    errorMessage: "No se pudo cargar la configuración de instancia.",
  });
}

export async function saveInstanceRegistration(
  open: boolean,
  csrfToken: string,
): Promise<InstanceSettings> {
  return apiClient.put<InstanceSettings>(
    apiRoutes.panel.instance.registration,
    { open },
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo cambiar la política de registro.",
    },
  );
}

export async function saveInstanceDefaultModules(
  modules: string[] | null,
  csrfToken: string,
): Promise<InstanceSettings> {
  return apiClient.put<InstanceSettings>(
    apiRoutes.panel.instance.modulesDefaults,
    { modules },
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo guardar los módulos por defecto.",
    },
  );
}

export async function saveInstanceHeartbeat(
  intervalSeconds: number | null,
  timeoutSeconds: number | null,
  csrfToken: string,
): Promise<InstanceSettings> {
  return apiClient.put<InstanceSettings>(
    apiRoutes.panel.instance.heartbeat,
    { intervalSeconds, timeoutSeconds },
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo guardar los defaults de heartbeat.",
    },
  );
}
