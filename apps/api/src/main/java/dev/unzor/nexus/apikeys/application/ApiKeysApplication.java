package dev.unzor.nexus.apikeys.application;

import dev.unzor.nexus.apikeys.application.service.ApiKeysHelloService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class ApiKeysApplication implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ApiKeysApplication.class);

    private final ApiKeysHelloService helloService;

    ApiKeysApplication(ApiKeysHelloService helloService) {
        this.helloService = helloService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info(helloService.status().message());
    }
}
