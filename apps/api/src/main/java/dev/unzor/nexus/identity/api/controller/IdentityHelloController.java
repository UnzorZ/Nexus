package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.identity.api.dto.IdentityModuleStatus;
import dev.unzor.nexus.identity.application.service.IdentityHelloService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/identity")
class IdentityHelloController {

    private final IdentityHelloService helloService;

    IdentityHelloController(IdentityHelloService helloService) {
        this.helloService = helloService;
    }

    @GetMapping("/hello")
    IdentityModuleStatus hello() {
        return helloService.status();
    }
}
