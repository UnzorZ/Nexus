package dev.unzor.nexus.projects.api.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.unzor.nexus.projects.application.service.CreateProjectService;
import dev.unzor.nexus.projects.api.dto.ProjectSlugReference;
import dev.unzor.nexus.projects.api.requests.CreateProjectRequest;
import dev.unzor.nexus.projects.domain.entity.Project;

@RestController
@RequestMapping("/api/projects")
class ProjectsCreateController {

    private final CreateProjectService createProjectService;

    ProjectsCreateController(CreateProjectService createProjectService) {
        this.createProjectService = createProjectService;
    }

    @PostMapping("/projects")
    public ProjectSlugReference createProject(@RequestBody CreateProjectRequest request) {
        Project project = createProjectService.createProject(request.getSlug(), 
        request.getName(), request.getDescription(), request.getPublicBaseUrl());
        return new ProjectSlugReference(project.getId(), project.getSlug());
    }
}