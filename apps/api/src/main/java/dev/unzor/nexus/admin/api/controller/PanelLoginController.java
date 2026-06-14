package dev.unzor.nexus.admin.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/panel")
class PanelLoginController {

    @GetMapping("/login")
    String login() {
        return "panel/login";
    }
}
