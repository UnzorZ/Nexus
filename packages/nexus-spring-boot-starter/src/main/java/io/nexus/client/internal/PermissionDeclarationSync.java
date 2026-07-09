package io.nexus.client.internal;

import io.nexus.client.NexusProperties;
import io.nexus.client.PermissionDeclarationProvider;
import io.nexus.client.api.PermissionDeclaration;
import io.nexus.client.api.PermissionDeclarationReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.ArrayList;
import java.util.List;

/**
 * Sincroniza los permisos declarados por la app (YAML {@code nexus.permissions.
 * declarations} + beans {@link PermissionDeclarationProvider}) con Nexus al
 * arrancar: POST {@code /api/v1/permissions/declare}. Fallos se loguean (no
 * impiden arrancar la app).
 */
public class PermissionDeclarationSync implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PermissionDeclarationSync.class);

    private final PermissionClient permissionClient;
    private final NexusProperties properties;
    private final List<PermissionDeclarationProvider> providers;

    public PermissionDeclarationSync(PermissionClient permissionClient, NexusProperties properties,
                                     List<PermissionDeclarationProvider> providers) {
        this.permissionClient = permissionClient;
        this.properties = properties;
        this.providers = providers == null ? List.of() : providers;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<PermissionDeclaration> all = new ArrayList<>();
        for (NexusProperties.Permissions.Declaration d : properties.getPermissions().getDeclarations()) {
            all.add(new PermissionDeclaration(d.getKey(), d.getLabel()));
        }
        for (PermissionDeclarationProvider provider : providers) {
            try {
                List<PermissionDeclaration> provided = provider.declarations();
                if (provided != null) {
                    all.addAll(provided);
                }
            } catch (RuntimeException e) {
                log.warn("PermissionDeclarationProvider {} failed: {}", provider.getClass().getName(), e.getMessage());
            }
        }
        // Enviamos siempre (incluso vacío) para que el backend reconcilie: si la
        // app eliminó todas sus declaraciones, el array vacío marca los permisos
        // previos de esta app como ausentes.
        try {
            PermissionDeclarationReceipt receipt = permissionClient.declare(all);
            log.info("Permisos declarados en Nexus: {} (creados {}, marcados missing {})",
                    receipt.declared(), receipt.created(), receipt.markedMissing());
        } catch (RuntimeException e) {
            log.warn("Permission declaration sync failed: {}", e.getMessage());
        }
    }
}
