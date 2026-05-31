package dev.unzor.nexus.admin.api.controller;

import dev.unzor.nexus.admin.api.dto.AdminModuleStatus;
import dev.unzor.nexus.admin.application.service.AdminHelloService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/admin")
class AdminHelloController {

    private final AdminHelloService helloService;

    AdminHelloController(AdminHelloService helloService) {
        this.helloService = helloService;
    }

    @GetMapping("/hello")
    AdminModuleStatus hello() {
        return helloService.status();
    }
}
