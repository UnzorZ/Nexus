package dev.unzor.nexus.registry.api.controller;

import dev.unzor.nexus.registry.api.dto.RegistryModuleStatus;
import dev.unzor.nexus.registry.application.service.RegistryHelloService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/registry")
class RegistryHelloController {

    private final RegistryHelloService helloService;

    RegistryHelloController(RegistryHelloService helloService) {
        this.helloService = helloService;
    }

    @GetMapping("/hello")
    RegistryModuleStatus hello() {
        return helloService.status();
    }
}
