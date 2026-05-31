package dev.unzor.nexus.identity.application;

import dev.unzor.nexus.identity.application.service.IdentityHelloService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class IdentityApplication implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IdentityApplication.class);

    private final IdentityHelloService helloService;

    IdentityApplication(IdentityHelloService helloService) {
        this.helloService = helloService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info(helloService.status().message());
    }
}
