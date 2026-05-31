package dev.unzor.nexus.projects.application;

import dev.unzor.nexus.projects.application.service.ProjectsHelloService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class ProjectsApplication implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProjectsApplication.class);

    private final ProjectsHelloService helloService;

    ProjectsApplication(ProjectsHelloService helloService) {
        this.helloService = helloService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info(helloService.status().message());
    }
}
