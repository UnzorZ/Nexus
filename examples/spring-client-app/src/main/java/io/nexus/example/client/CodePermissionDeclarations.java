package io.nexus.example.client;

import io.nexus.client.PermissionDeclarationProvider;
import io.nexus.client.api.PermissionDeclaration;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Demuestra la declaración de permisos <b>desde código</b> (además de los del
 * YAML). El starter recoge este bean (vía {@link PermissionDeclarationProvider})
 * y los sincroniza con Nexus al arrancar, junto con los de
 * {@code nexus.permissions.declarations}.
 */
@Component
public class CodePermissionDeclarations implements PermissionDeclarationProvider {

    @Override
    public List<PermissionDeclaration> declarations() {
        return List.of(
                PermissionDeclaration.of("reports.export", "Exportar informes"));
    }
}
