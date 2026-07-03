package dev.unzor.nexus.admin.domain.exception;

/**
 * Lanzada al intentar registrar una cuenta cuando la instancia tiene el registro
 * cerrado (configuración del operador). El bootstrap del primer admin (ADR-0010)
 * siempre se permite si aún no existe admin. Se traduce a 409
 * {@code registration_closed}.
 */
public class RegistrationClosedException extends RuntimeException {

    public RegistrationClosedException() {
        super("Registration is closed on this instance.");
    }
}
