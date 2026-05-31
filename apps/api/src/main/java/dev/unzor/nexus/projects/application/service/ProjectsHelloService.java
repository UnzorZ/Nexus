package dev.unzor.nexus.projects.application.service;

import dev.unzor.nexus.projects.api.dto.ProjectsModuleStatus;
import org.springframework.stereotype.Service;

@Service
public class ProjectsHelloService {

    public ProjectsModuleStatus status() {
        return new ProjectsModuleStatus("projects", "UP", "projects module started");
    }
}
