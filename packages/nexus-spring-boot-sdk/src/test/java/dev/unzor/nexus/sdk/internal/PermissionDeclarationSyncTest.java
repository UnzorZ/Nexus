package dev.unzor.nexus.sdk.internal;

import dev.unzor.nexus.sdk.NexusProperties;
import dev.unzor.nexus.sdk.PermissionDeclarationProvider;
import dev.unzor.nexus.sdk.api.PermissionDeclaration;
import dev.unzor.nexus.sdk.api.PermissionDeclarationReceipt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regresión del fix P2: la sincronización declarativa debe POSTear SIEMPRE al
 * backend, incluso con cero declaraciones. Antes había un
 * {@code if (all.isEmpty()) return;} que saltaba el POST, de modo que eliminar
 * todas las declaraciones nunca marcaba los permisos previos de la app como
 * ausentes (el backend nunca recibía el array vacío para reconciliar).
 */
class PermissionDeclarationSyncTest {

    private final PermissionClient permissionClient = mock(PermissionClient.class);
    private final NexusProperties properties = new NexusProperties();

    @Test
    void emptyDeclarationsStillPostsSoBackendReconciles() {
        when(permissionClient.declare(any()))
                .thenReturn(new PermissionDeclarationReceipt(0, 0, 0));

        new PermissionDeclarationSync(permissionClient, properties, List.of()).run(null);

        // El POST debe ocurrir aunque no haya declaraciones — lista vacía, nunca saltado.
        verify(permissionClient).declare(List.of());
    }

    @Test
    void mergesYamlAndProviderDeclarationsAndPostsThem() {
        NexusProperties.Permissions.Declaration yaml = new NexusProperties.Permissions.Declaration();
        yaml.setKey("orders.read");
        yaml.setLabel("Ver pedidos");
        properties.getPermissions().getDeclarations().add(yaml);

        PermissionDeclarationProvider provider = () -> List.of(
                new PermissionDeclaration("orders.cancel", "Cancelar pedidos"));
        when(permissionClient.declare(any()))
                .thenReturn(new PermissionDeclarationReceipt(2, 2, 0));

        new PermissionDeclarationSync(permissionClient, properties, List.of(provider)).run(null);

        org.mockito.ArgumentCaptor<List<PermissionDeclaration>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(permissionClient).declare(captor.capture());
        assertThat(captor.getValue())
                .extracting(PermissionDeclaration::key)
                .containsExactlyInAnyOrder("orders.read", "orders.cancel");
    }

    @Test
    void providerFailureDoesNotAbortThePost() {
        PermissionDeclarationProvider failing = () -> { throw new IllegalStateException("boom"); };
        when(permissionClient.declare(any()))
                .thenReturn(new PermissionDeclarationReceipt(0, 0, 0));

        new PermissionDeclarationSync(permissionClient, properties, List.of(failing)).run(null);

        // Aunque un provider falle, seguimos POSTeando lo recolectado (aquí, vacío).
        verify(permissionClient).declare(List.of());
    }
}
