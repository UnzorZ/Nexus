package dev.unzor.nexus.audit.application;

import dev.unzor.nexus.audit.application.service.AuditHelloService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class AuditApplication implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuditApplication.class);

    private final AuditHelloService helloService;

    AuditApplication(AuditHelloService helloService) {
        this.helloService = helloService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info(helloService.status().message());
    }
}
