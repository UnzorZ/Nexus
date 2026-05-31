package dev.unzor.nexus.modules.api.controller;

import dev.unzor.nexus.modules.api.dto.ModulesModuleStatus;
import dev.unzor.nexus.modules.application.service.ModulesHelloService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/modules")
class ModulesHelloController {

    private final ModulesHelloService helloService;

    ModulesHelloController(ModulesHelloService helloService) {
        this.helloService = helloService;
    }

    @GetMapping("/hello")
    ModulesModuleStatus hello() {
        return helloService.status();
    }
}
