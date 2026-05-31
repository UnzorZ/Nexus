package dev.unzor.nexus.registry.application;

import dev.unzor.nexus.registry.application.service.RegistryHelloService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class RegistryApplication implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RegistryApplication.class);

    private final RegistryHelloService helloService;

    RegistryApplication(RegistryHelloService helloService) {
        this.helloService = helloService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info(helloService.status().message());
    }
}
