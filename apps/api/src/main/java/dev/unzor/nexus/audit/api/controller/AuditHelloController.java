package dev.unzor.nexus.audit.api.controller;

import dev.unzor.nexus.audit.api.dto.AuditModuleStatus;
import dev.unzor.nexus.audit.application.service.AuditHelloService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/audit")
class AuditHelloController {

    private final AuditHelloService helloService;

    AuditHelloController(AuditHelloService helloService) {
        this.helloService = helloService;
    }

    @GetMapping("/hello")
    AuditModuleStatus hello() {
        return helloService.status();
    }
}
