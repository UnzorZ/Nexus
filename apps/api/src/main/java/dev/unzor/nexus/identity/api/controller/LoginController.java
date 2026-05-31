package dev.unzor.nexus.identity.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class LoginController {

    @GetMapping("/login")
    String login() {
        return "identity/login";
    }
}
