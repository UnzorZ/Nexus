package dev.unzor.nexus.instance.api.controller;

import dev.unzor.nexus.TestcontainersConfiguration;
import dev.unzor.nexus.admin.domain.entity.NexusAccount;
import dev.unzor.nexus.admin.persistence.repository.NexusAccountRepository;
import dev.unzor.nexus.instance.persistence.repository.InstanceSettingsRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Panel del operador (configuración de instancia): el SMTP lo sirve notify en
 * {@code /instance/smtp}; el status, este módulo en {@code /instance/status}.
 * Todos los endpoints requieren {@code ROLE_INSTANCE_ADMIN} (403 si no).
 *
 * <p>Como la BD se comparte entre tests y sólo puede existir un admin de instancia
 * (índice único parcial), cada test garantiza que SU cuenta sea el admin único
 * antes de loguearse.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InstanceSettingsIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NexusAccountRepository accountRepository;

    @Autowired
    private InstanceSettingsRepository instanceSettingsRepository;

    /**
     * La BD se comparte entre tests: cualquier mutación de instance_settings
     * (registro, módulos por defecto, heartbeat) persistiría y envenenaría al
     * resto de la suite (p. ej. registro cerrado rompería todas las altas). Se
     * resetea a defaults tras cada test.
     */
    @AfterEach
    void resetInstanceSettings() {
        instanceSettingsRepository.findById((short) 1).ifPresent(s -> {
            s.setRegistrationOpen(true, null);
            s.setDefaultModules(null, null);
            s.setHeartbeat(null, null, null);
            instanceSettingsRepository.save(s);
        });
    }

    @Test
    void adminCanManageInstanceSmtp() throws Exception {
        String email = unique("inst-admin");
        registerAccount(email);
        makeSoleInstanceAdmin(email);
        LoginSession admin = login(email);

        // Sin configurar inicialmente.
        mockMvc.perform(get("/api/panel/v1/instance/smtp").cookie(admin.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordConfigured").value(false));

        // Guarda SMTP de instancia (host IP público literal: sin DNS en el guard SSRF).
        mockMvc.perform(put("/api/panel/v1/instance/smtp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", admin.csrfToken())
                        .cookie(admin.csrfCookie(), admin.sessionCookie())
                        .content("{\"host\":\"8.8.8.8\",\"port\":587,\"username\":\"u\","
                                + "\"from\":\"ops@nexus.test\",\"password\":\"pw\",\"tlsMode\":\"PUBLIC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.host").value("8.8.8.8"))
                .andExpect(jsonPath("$.passwordConfigured").value(true));

        // Persistido.
        mockMvc.perform(get("/api/panel/v1/instance/smtp").cookie(admin.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.host").value("8.8.8.8"))
                .andExpect(jsonPath("$.from").value("ops@nexus.test"));
    }

    @Test
    void nonAdminIsForbidden() throws Exception {
        String adminEmail = unique("inst-admin2");
        String userEmail = unique("inst-user");
        registerAccount(userEmail);
        registerAccount(adminEmail);
        makeSoleInstanceAdmin(adminEmail);
        LoginSession user = login(userEmail);

        mockMvc.perform(get("/api/panel/v1/instance/smtp").cookie(user.sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("instance_admin_required"));

        mockMvc.perform(get("/api/panel/v1/instance/status").cookie(user.sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("instance_admin_required"));
    }

    @Test
    void adminCanReadStatus() throws Exception {
        String email = unique("inst-status-admin");
        registerAccount(email);
        makeSoleInstanceAdmin(email);
        LoginSession admin = login(email);

        mockMvc.perform(get("/api/panel/v1/instance/status").cookie(admin.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registration.policy").value("open"))
                .andExpect(jsonPath("$.vaultMasterKey.status").exists())
                .andExpect(jsonPath("$.jwtKeystore.status").exists());
    }

    @Test
    void adminCanEditInstanceSettings() throws Exception {
        String email = unique("inst-edit-admin");
        registerAccount(email);
        makeSoleInstanceAdmin(email);
        LoginSession admin = login(email);

        // Defaults: registro abierto, sin overrides.
        mockMvc.perform(get("/api/panel/v1/instance/settings").cookie(admin.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationOpen").value(true))
                .andExpect(jsonPath("$.defaultModules").doesNotExist()) // null -> catálogo
                .andExpect(jsonPath("$.heartbeat.intervalSeconds").doesNotExist());

        // Cierra el registro.
        mockMvc.perform(put("/api/panel/v1/instance/registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", admin.csrfToken())
                        .cookie(admin.csrfCookie(), admin.sessionCookie())
                        .content("{\"open\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationOpen").value(false));

        // Defaults de heartbeat válidos.
        mockMvc.perform(put("/api/panel/v1/instance/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", admin.csrfToken())
                        .cookie(admin.csrfCookie(), admin.sessionCookie())
                        .content("{\"intervalSeconds\":20,\"timeoutSeconds\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heartbeat.intervalSeconds").value(20))
                .andExpect(jsonPath("$.heartbeat.timeoutSeconds").value(60));

        // Heartbeat inválido (interval > timeout) -> 400 validation_error.
        mockMvc.perform(put("/api/panel/v1/instance/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", admin.csrfToken())
                        .cookie(admin.csrfCookie(), admin.sessionCookie())
                        .content("{\"intervalSeconds\":10,\"timeoutSeconds\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));

        // Módulos por defecto: activar sólo identity + audit.
        mockMvc.perform(put("/api/panel/v1/instance/modules-defaults")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", admin.csrfToken())
                        .cookie(admin.csrfCookie(), admin.sessionCookie())
                        .content("{\"modules\":[\"identity\",\"audit\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultModules[0]").value("identity"));
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/panel/v1/instance/status"))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers -----------------------------------------------------------------

    /**
     * Garantiza que {@code email} sea el único admin de instancia: revoca
     * cualquier admin previo (índice único parcial) y se lo otorga a esta cuenta.
     * Debe llamarse ANTES de login para que la sesión incluya ROLE_INSTANCE_ADMIN.
     */
    private void makeSoleInstanceAdmin(String email) {
        for (NexusAccount account : accountRepository.findAll()) {
            if (account.isInstanceAdmin()) {
                account.revokeInstanceAdmin();
                accountRepository.save(account);
            }
        }
        NexusAccount target = accountRepository.findByEmailIgnoreCase(email).orElseThrow();
        target.grantInstanceAdmin();
        accountRepository.save(target);
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

    private record LoginSession(Cookie sessionCookie, String csrfToken, Cookie csrfCookie) {
    }
}
