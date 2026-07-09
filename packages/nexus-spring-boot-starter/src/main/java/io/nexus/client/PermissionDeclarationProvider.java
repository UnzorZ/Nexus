package io.nexus.client;

import io.nexus.client.api.PermissionDeclaration;

import java.util.List;

/**
 * SPI para declarar permisos desde código. Las apps implementan este bean y el
 * starter los recoge (junto con los de YAML {@code nexus.permissions.declarations})
 * y los sincroniza con Nexus al arrancar.
 *
 * <pre>
 *   &#64;Component
 *   class MyPermissions implements PermissionDeclarationProvider {
 *       public List&lt;PermissionDeclaration&gt; declarations() {
 *           return List.of(PermissionDeclaration.of("orders.read", "Ver pedidos"));
 *       }
 *   }
 * </pre>
 */
public interface PermissionDeclarationProvider {

    List<PermissionDeclaration> declarations();
}
