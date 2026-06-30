package dev.unzor.nexus.apikeys.api.controller;

import dev.unzor.nexus.apikeys.api.RequiredScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller de prueba (solo tests) que declara un scope requerido, para
 * ejercitar la rama {@code 403 missing_scope} del interceptor. Se importa
 * explícitamente en {@link ProjectApiRuntimeTests}.
 */
@RestController
@RequestMapping("/api/v1/test")
class TestScopedController {

    @GetMapping("/scoped")
    @RequiredScope("test:scoped")
    Map<String, Object> scoped() {
        return Map.of("ok", true);
    }
}
