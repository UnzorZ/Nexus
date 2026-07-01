package dev.unzor.nexus.modules.gate;

import dev.unzor.nexus.TestcontainersConfiguration;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MatrixBypassCheckTest {
    @Autowired MockMvc mockMvc;
    @Autowired ProjectModuleRepository moduleRepository;
    @Autowired tools.jackson.databind.ObjectMapper objectMapper;

    @Test void matrix() throws Exception {
        String email = "matrix-" + UUID.randomUUID() + "@example.com";
        Cookie csrf = csrf();
        mockMvc.perform(post("/api/panel/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .header("X-XSRF-TOKEN", csrf.getValue()).cookie(csrf)
                .content("{\"email\":\"" + email + "\",\"password\":\"plain-password\",\"displayName\":\"Tester\"}"))
                .andExpect(status().isCreated());
        csrf = csrf();
        MvcResult login = mockMvc.perform(post("/panel/login").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .header("User-Agent", "test").param("username", email).param("password", "plain-password"))
                .andExpect(status().is3xxRedirection()).andReturn();
        Cookie session = login.getResponse().getCookie("JSESSIONID");
        MvcResult created = mockMvc.perform(post("/api/panel/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .header("X-XSRF-TOKEN", csrf.getValue()).cookie(csrf, session)
                .content("{\"slug\":\"mx-" + UUID.randomUUID().toString().replace("-", "").substring(0,12) + "\",\"name\":\"Gate Test\"}"))
                .andExpect(status().isCreated()).andReturn();
        String projectId = objectMapper.readTree(created.getResponse().getContentAsString()).at("/id").asText();
        moduleRepository.save(new ProjectModule(UUID.fromString(projectId), NexusModule.PERMISSIONS, false));
        MvcResult result = mockMvc.perform(get("/api/panel/v1/projects/" + projectId + "/;x=y/permissions").cookie(session))
                .andReturn();
        System.out.println("MATRIX STATUS=" + result.getResponse().getStatus() + " BODY=" + result.getResponse().getContentAsString());
    }
    Cookie csrf() throws Exception { MvcResult r = mockMvc.perform(get("/api/panel/v1/csrf")).andExpect(status().isOk()).andReturn(); return r.getResponse().getCookie("XSRF-TOKEN"); }
}
