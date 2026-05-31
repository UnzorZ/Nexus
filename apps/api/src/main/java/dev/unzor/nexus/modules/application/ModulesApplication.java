package dev.unzor.nexus.modules.application;

import dev.unzor.nexus.modules.application.service.ModulesHelloService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class ModulesApplication implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ModulesApplication.class);

    private final ModulesHelloService helloService;

    ModulesApplication(ModulesHelloService helloService) {
        this.helloService = helloService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info(helloService.status().message());
    }
}
