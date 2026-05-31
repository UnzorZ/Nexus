package dev.unzor.nexus.permissions.application;

import dev.unzor.nexus.permissions.application.service.PermissionsHelloService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class PermissionsApplication implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PermissionsApplication.class);

    private final PermissionsHelloService helloService;

    PermissionsApplication(PermissionsHelloService helloService) {
        this.helloService = helloService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info(helloService.status().message());
    }
}
