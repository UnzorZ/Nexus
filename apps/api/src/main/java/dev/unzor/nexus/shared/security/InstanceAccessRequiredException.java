package dev.unzor.nexus.shared.security;

/**
 * Lanzada cuando un endpoint del panel del operador (configuración de instancia)
 * se invoca sin el rol {@code ROLE_INSTANCE_ADMIN}. Se traduce a 403
 * {@code instance_admin_required} por el advice del módulo que la lanza.
 *
 * <p>Vive en {@code shared.security} para que tanto el controlador de SMTP de
 * instancia (módulo {@code notify}) como el de status (módulo {@code instance})
 * la compartan sin acoplar módulos entre sí.</p>
 */
public class InstanceAccessRequiredException extends RuntimeException {

    public InstanceAccessRequiredException() {
        super("Instance admin role is required for this operation.");
    }
}
