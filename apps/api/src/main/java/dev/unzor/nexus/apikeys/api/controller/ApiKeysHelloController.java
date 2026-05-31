package dev.unzor.nexus.apikeys.api.controller;

import dev.unzor.nexus.apikeys.api.dto.ApiKeysModuleStatus;
import dev.unzor.nexus.apikeys.application.service.ApiKeysHelloService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/apikeys")
class ApiKeysHelloController {

    private final ApiKeysHelloService helloService;

    ApiKeysHelloController(ApiKeysHelloService helloService) {
        this.helloService = helloService;
    }

    @GetMapping("/hello")
    ApiKeysModuleStatus hello() {
        return helloService.status();
    }
}
