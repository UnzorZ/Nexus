package dev.unzor.nexus.notify.api.controller;

import dev.unzor.nexus.notify.api.dto.NotifyModuleStatus;
import dev.unzor.nexus.notify.application.service.NotifyHelloService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/notify")
class NotifyHelloController {

    private final NotifyHelloService helloService;

    NotifyHelloController(NotifyHelloService helloService) {
        this.helloService = helloService;
    }

    @GetMapping("/hello")
    NotifyModuleStatus hello() {
        return helloService.status();
    }
}
