package dev.unzor.nexus.modules.gate;

import dev.unzor.nexus.TestcontainersConfiguration;
import dev.unzor.nexus.admin.domain.entity.NexusAccount;
import dev.unzor.nexus.admin.persistence.repository.NexusAccountRepository;
import dev.unzor.nexus.modules.domain.entity.ProjectModule;
import dev.unzor.nexus.modules.domain.enums.NexusModule;
import dev.unzor.nexus.modules.persistence.repository.ProjectModuleRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validación end-to-end del module gate: módulos deshabilitados responden
 * {@code 403 module_disabled} tanto en el panel como en el runtime, los endpoints
 * sin módulo gateable no se ven afectados, el gate no lo sortea ni siquiera el
 * instance admin, y la gestión de módulos ({@code /modules}) permanece accesible
 * (sin lockout).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ModuleGateIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectModuleRepository moduleRepository;

    @Autowired
    private NexusAccountRepository accountRepository;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    @Test
    void enabledModulesAreReachableByDefault() throws Exception {
        String ownerEmail = unique("gate-ctrl");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("gate"));

        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/permissions", projectId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/roles", projectId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/audit", projectId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/heartbeats", projectId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isOk());
    }

    @Test
    void disabledPermissionsRejectsPermissionsAndRolesButNotAudit() throws Exception {
        String ownerEmail = unique("gate-perm");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("gate"));

        setModule(projectId, NexusModule.PERMISSIONS, false);

        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/permissions", projectId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("module_disabled"))
                .andExpect(jsonPath("$.detail").value("The permissions module is disabled for this project."))
                .andExpect(jsonPath("$.traceId").exists());

        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/roles", projectId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("module_disabled"));

        // Otro módulo (AUDIT) sigue habilitado → accesible.
        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/audit", projectId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isOk());
    }

    @Test
    void disabledAuditRejectsAuditReadAndRecoversAfterReEnable() throws Exception {
        String ownerEmail = unique("gate-audit");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("gate"));

        setModule(projectId, NexusModule.AUDIT, false);
        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/audit", projectId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("module_disabled"));

        setModule(projectId, NexusModule.AUDIT, true);
        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/audit", projectId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isOk());
    }

    @Test
    void disabledRegistryRejectsPanelHeartbeatsAndRuntimeBeat() throws Exception {
        String ownerEmail = unique("gate-reg");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("gate"));
        String key = createKey(owner, projectId); // scope registry:heartbeat

        // El latido funciona con REGISTRY habilitado.
        mockMvc.perform(post("/api/v1/registry/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Api-Key", key)
                        .content(heartbeatBody()))
                .andExpect(status().isOk());

        setModule(projectId, NexusModule.REGISTRY, false);

        // Panel: la lectura de heartbeats se bloquea.
        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/heartbeats", projectId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("module_disabled"));

        // Runtime: el latido (con API key válida y scope correcto) se bloquea por el gate.
        mockMvc.perform(post("/api/v1/registry/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Api-Key", key)
                        .content(heartbeatBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("module_disabled"));

        setModule(projectId, NexusModule.REGISTRY, true);
        mockMvc.perform(post("/api/v1/registry/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Nexus-Api-Key", key)
                        .content(heartbeatBody()))
                .andExpect(status().isOk());
    }

    @Test
    void ungatedEndpointsAreUnaffectedByDisabledModule() throws Exception {
        String ownerEmail = unique("gate-ungated");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("gate"));

        setModule(projectId, NexusModule.PERMISSIONS, false);

        // members, api-keys y los settings del proyecto no tienen módulo gateable.
        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/members", projectId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/api-keys", projectId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/panel/v1/projects/{projectId}", projectId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isOk());
    }

    @Test
    void instanceAdminDoesNotBypassTheGate() throws Exception {
        String ownerEmail = unique("gate-owner");
        String adminEmail = unique("gate-admin");
        registerAccount(ownerEmail);
        registerAccount(adminEmail);
        makeSoleInstanceAdmin(adminEmail);
        LoginSession owner = login(ownerEmail);
        LoginSession admin = login(adminEmail);
        String projectId = createProject(owner, randomSlug("gate"));

        // Con PERMISSIONS habilitado, el instance admin (no miembro) accede vía bypass de requireAccess.
        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/permissions", projectId)
                        .cookie(admin.sessionCookie()))
                .andExpect(status().isOk());

        // Con PERMISSIONS deshabilitado, el gate bloquea al admin exactamente igual.
        setModule(projectId, NexusModule.PERMISSIONS, false);
        mockMvc.perform(get("/api/panel/v1/projects/{projectId}/permissions", projectId)
                        .cookie(admin.sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("module_disabled"));
    }

    @Test
    void modulesManagementStaysReachableNoLockout() throws Exception {
        String ownerEmail = unique("gate-lockout");
        registerAccount(ownerEmail);
        LoginSession owner = login(ownerEmail);
        String projectId = createProject(owner, randomSlug("gate"));

        // Varios módulos apagados a la vez…
        setModule(projectId, NexusModule.PERMISSIONS, false);
        setModule(projectId, NexusModule.AUDIT, false);
        setModule(projectId, NexusModule.REGISTRY, false);

        // …pero /modules nunca se gatea → el propietario siempre puede re-encender.
        mockMvc.perform(patch("/api/panel/v1/projects/{projectId}/modules/{key}", projectId, "audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"enabled\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("audit"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    // --- helpers -----------------------------------------------------------------

    private void setModule(String projectId, NexusModule module, boolean enabled) {
        UUID pid = UUID.fromString(projectId);
        ProjectModule row = moduleRepository.findByProjectIdAndModule(pid, module)
                .orElseGet(() -> new ProjectModule(pid, module, enabled));
        row.setEnabled(enabled);
        moduleRepository.save(row);
    }

    /**
     * Convierte a {@code email} en el ÚNICO instance admin: revoca cualquier admin
     * preexistente (el bootstrap del DB compartido entre tests) y se lo concede a
     * esta cuenta. Determinista y sin chocar con el índice único parcial
     * {@code uk_nexus_accounts_one_instance_admin}. Tras el login posterior la
     * cuenta queda con {@code ROLE_INSTANCE_ADMIN}.
     */
    private void makeSoleInstanceAdmin(String email) {
        accountRepository.findAll().stream()
                .filter(NexusAccount::isInstanceAdmin)
                .forEach(account -> {
                    account.revokeInstanceAdmin();
                    accountRepository.save(account);
                });
        NexusAccount account = accountRepository.findByEmailIgnoreCase(email).orElseThrow();
        account.grantInstanceAdmin();
        accountRepository.save(account);
    }

    private String createKey(LoginSession owner, String projectId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/panel/v1/projects/{projectId}/api-keys", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"name\":\"CI\",\"scopes\":[\"registry:heartbeat\"],\"expiresAt\":null}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/secret").asText();
    }

    private String createProject(LoginSession owner, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/panel/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .cookie(owner.csrfCookie(), owner.sessionCookie())
                        .content("{\"slug\":\"" + slug + "\",\"name\":\"Gate Test\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/id").asText();
    }

    private void registerAccount(String email) throws Exception {
        Cookie csrfCookie = fetchCsrfCookie();
        mockMvc.perform(post("/api/panel/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .cookie(csrfCookie)
                        .content("{\"email\":\"" + email + "\",\"password\":\"plain-password\",\"displayName\":\"Tester\"}"))
                .andExpect(status().isCreated());
    }

    private LoginSession login(String email) throws Exception {
        Cookie csrfCookie = fetchCsrfCookie();
        MvcResult loginResult = mockMvc.perform(post("/panel/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .header("User-Agent", "Mozilla/5.0 (Test Runner)")
                        .param("username", email)
                        .param("password", "plain-password"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Cookie sessionCookie = cookieByName(loginResult, "JSESSIONID");
        if (sessionCookie == null) {
            throw new IllegalStateException("JSESSIONID not issued after login");
        }
        return new LoginSession(sessionCookie, csrfCookie.getValue(), csrfCookie);
    }

    private Cookie fetchCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/panel/v1/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        if (cookie == null) {
            throw new IllegalStateException("XSRF-TOKEN cookie was not issued");
        }
        return cookie;
    }

    private static String heartbeatBody() {
        return "{\"instanceId\":\"demo-api-01\",\"appName\":\"demo-api\",\"appVersion\":\"1.0.0\","
                + "\"status\":\"up\",\"metadata\":{\"javaVersion\":\"21\"}}";
    }

    private static Cookie cookieByName(MvcResult result, String name) {
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie;
            }
        }
        return null;
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private static String randomSlug(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private record LoginSession(Cookie sessionCookie, String csrfToken, Cookie csrfCookie) {
    }
}
