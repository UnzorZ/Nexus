package dev.unzor.nexus.admin.application.events;

import dev.unzor.nexus.admin.application.service.PanelSessionService;
import dev.unzor.nexus.admin.domain.events.NexusAccountSessionsRevocationRequested;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Revoca todas las sesiones del panel de una cuenta cuando el agregado
 * {@code NexusAccount} lo solicita (suspensión, desactivación, retirada de
 * {@code instanceAdmin} y, en el futuro, cambio de contraseña).
 *
 * <p>Se ejecuta tras el commit de la transacción que persistió el cambio, dentro del
 * módulo {@code admin}, de modo que la revocación solo ocurre si el cambio de estado
 * se consolidó en PostgreSQL. La entrega del evento es <em>al menos una vez</em>:
 * Spring Modulith persiste las publicaciones en PostgreSQL y la reentrega periódica
 * ({@code PanelSessionRevocationRepublisher}) garantiza que la revocación se complete
 * aunque Redis falle justo después del commit. La operación es idempotente.</p>
 */
@Component
class PanelSessionRevocationListener {

    private final PanelSessionService panelSessionService;

    PanelSessionRevocationListener(PanelSessionService panelSessionService) {
        this.panelSessionService = panelSessionService;
    }

    @ApplicationModuleListener
    void onSessionsRevocationRequested(NexusAccountSessionsRevocationRequested event) {
        panelSessionService.revokeAllForAccount(event.accountId());
    }
}
