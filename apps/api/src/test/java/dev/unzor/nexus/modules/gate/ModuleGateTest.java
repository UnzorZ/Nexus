package dev.unzor.nexus.modules.gate;

import dev.unzor.nexus.apikeys.security.ResolvedApiKey;
import dev.unzor.nexus.modules.domain.enums.NexusModule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test unitario del resolver {@link ModuleGate} (sin contexto Spring): cubre cada
 * segmento del panel (gateados y no gateados), las dos superficies runtime, UUIDs
 * malformados y la ausencia de principal.
 */
class ModuleGateTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final ResolvedApiKey KEY = new ResolvedApiKey(
            PROJECT_ID, UUID.randomUUID(), "nxs_proj_abc", List.of("registry:heartbeat"));

    private final ModuleGate gate = new ModuleGate();

    // --- panel: segmentos gateados ---

    @Test
    void panelPermissionsIsGatedToPermissionsModule() {
        assertThat(gate.resolve(panel("permissions"), null))
                .contains(new ModuleGate.GatedRequest(PROJECT_ID, NexusModule.PERMISSIONS));
    }

    @Test
    void panelRolesIsGatedToPermissionsModule() {
        assertThat(gate.resolve(panel("roles"), null))
                .contains(new ModuleGate.GatedRequest(PROJECT_ID, NexusModule.PERMISSIONS));
    }

    @Test
    void panelDeepPermissionPathStillGatesByFirstSegment() {
        assertThat(gate.resolve(panel("permissions") + "/some-id", null))
                .contains(new ModuleGate.GatedRequest(PROJECT_ID, NexusModule.PERMISSIONS));
    }

    @Test
    void panelAuditIsGatedToAuditModule() {
        assertThat(gate.resolve(panel("audit"), null))
                .contains(new ModuleGate.GatedRequest(PROJECT_ID, NexusModule.AUDIT));
    }

    @Test
    void panelHeartbeatsIsGatedToRegistryModule() {
        assertThat(gate.resolve(panel("heartbeats"), null))
                .contains(new ModuleGate.GatedRequest(PROJECT_ID, NexusModule.REGISTRY));
    }

    // --- panel: segmentos NO gateados ---

    @Test
    void panelRootSettingsIsUngated() {
        assertThat(gate.resolve("/api/panel/v1/projects/" + PROJECT_ID, null)).isEmpty();
    }

    @Test
    void panelModulesManagementIsNeverGated() {
        assertThat(gate.resolve(panel("modules"), null)).isEmpty();
        assertThat(gate.resolve(panel("modules") + "/audit", null)).isEmpty();
    }

    @Test
    void panelMembersAndApiKeysAreUngated() {
        assertThat(gate.resolve(panel("members"), null)).isEmpty();
        assertThat(gate.resolve(panel("api-keys"), null)).isEmpty();
    }

    @Test
    void panelMalformedProjectIdIsUngated() {
        // UUID inválido: el controlador responderá 400/404; el gate no intenta gatearlo.
        assertThat(gate.resolve("/api/panel/v1/projects/not-a-uuid/permissions", null)).isEmpty();
    }

    // --- runtime ---

    @Test
    void runtimeRegistryIsGatedFromApiKeyPrincipal() {
        assertThat(gate.resolve("/api/v1/registry/heartbeat", KEY))
                .contains(new ModuleGate.GatedRequest(PROJECT_ID, NexusModule.REGISTRY));
    }

    @Test
    void runtimeWhoamiIsUngated() {
        assertThat(gate.resolve("/api/v1/whoami", KEY)).isEmpty();
    }

    @Test
    void runtimeRegistryWithoutApiKeyPrincipalIsUngated() {
        // Sin ResolvedApiKey el filtro de auth ya habría rechazado; el gate no falla.
        assertThat(gate.resolve("/api/v1/registry/heartbeat", null)).isEmpty();
    }

    // --- rutas ajenas ---

    @Test
    void unrelatedPathsAreUngated() {
        assertThat(gate.resolve("/api/panel/v1/csrf", null)).isEmpty();
        assertThat(gate.resolve("/api/panel/v1/accounts", null)).isEmpty();
        assertThat(gate.resolve("/internal/projects", null)).isEmpty();
        assertThat(gate.resolve(null, null)).isEmpty();
    }

    private static String panel(String segment) {
        return "/api/panel/v1/projects/" + PROJECT_ID + "/" + segment;
    }
}
