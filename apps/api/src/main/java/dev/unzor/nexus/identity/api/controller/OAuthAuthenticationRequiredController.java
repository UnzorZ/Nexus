package dev.unzor.nexus.identity.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class OAuthAuthenticationRequiredController {

    @GetMapping("/oauth2/authentication-required")
    String authenticationRequired() {
        return "identity/oauth2-authentication-required";
    }
}
