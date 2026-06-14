package dev.unzor.nexus.identity.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Callback técnico registrado en el cliente OAuth bootstrap. No lo consume el panel.
 */
@RestController
class OAuthBootstrapCallbackController {

    @GetMapping("/oauth2/bootstrap/callback")
    ResponseEntity<Void> callback() {
        return ResponseEntity.noContent().build();
    }
}
