/**
 * Tipos de respuesta compartidos entre features del panel (p. ej. el chequeo de
 * conexión SMTP que exponen tanto el módulo notify como la configuración de
 * instancia). Una sola fuente para evitar definiciones duplicadas.
 */
export type SmtpConnectionCheck = { ok: boolean; message: string };
