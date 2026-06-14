package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.identity.application.context.ProjectAuthenticationContext;
import dev.unzor.nexus.identity.application.service.ProjectSlugResolver;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/p/{projectSlug}")
class ProjectLoginController {

    private final ProjectSlugResolver projectSlugResolver;

    ProjectLoginController(ProjectSlugResolver projectSlugResolver) {
        this.projectSlugResolver = projectSlugResolver;
    }

    @GetMapping("/login")
    String login(@PathVariable String projectSlug, Model model) {
        try {
            ProjectAuthenticationContext context = projectSlugResolver.resolve(projectSlug);
            model.addAttribute("projectSlug", context.projectSlug());
            model.addAttribute("projectId", context.projectId());
            return "identity/project-login-reserved";
        }
        catch (ProjectNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
    }
}
