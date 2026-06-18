package dev.unzor.nexus.shared.security;

import java.util.UUID;

/**
 * Contrato mínimo que expone el identificador de la cuenta autenticada.
 *
 * <p>Cualquier módulo puede referenciar esta interfaz sin depender del
 * módulo {@code admin}. El principal del panel la implementa.</p>
 */
public interface AuthenticatedAccount {

    UUID accountId();
}
