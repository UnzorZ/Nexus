package dev.unzor.nexus.permissions.api.controller;

import dev.unzor.nexus.permissions.api.dto.PermissionsModuleStatus;
import dev.unzor.nexus.permissions.application.service.PermissionsHelloService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/permissions")
class PermissionsHelloController {

    private final PermissionsHelloService helloService;

    PermissionsHelloController(PermissionsHelloService helloService) {
        this.helloService = helloService;
    }

    @GetMapping("/hello")
    PermissionsModuleStatus hello() {
        return helloService.status();
    }
}
