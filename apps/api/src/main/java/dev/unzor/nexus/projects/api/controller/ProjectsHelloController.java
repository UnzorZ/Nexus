package dev.unzor.nexus.projects.api.controller;

import dev.unzor.nexus.projects.api.dto.ProjectsModuleStatus;
import dev.unzor.nexus.projects.application.service.ProjectsHelloService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/projects")
class ProjectsHelloController {

    private final ProjectsHelloService helloService;

    ProjectsHelloController(ProjectsHelloService helloService) {
        this.helloService = helloService;
    }

    @GetMapping("/hello")
    ProjectsModuleStatus hello() {
        return helloService.status();
    }
}
