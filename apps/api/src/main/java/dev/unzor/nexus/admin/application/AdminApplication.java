package dev.unzor.nexus.admin.application;

import dev.unzor.nexus.admin.application.service.AdminHelloService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class AdminApplication implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminApplication.class);

    private final AdminHelloService helloService;

    AdminApplication(AdminHelloService helloService) {
        this.helloService = helloService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info(helloService.status().message());
    }
}
