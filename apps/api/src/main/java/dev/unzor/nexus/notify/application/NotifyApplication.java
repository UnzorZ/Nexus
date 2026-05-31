package dev.unzor.nexus.notify.application;

import dev.unzor.nexus.notify.application.service.NotifyHelloService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class NotifyApplication implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NotifyApplication.class);

    private final NotifyHelloService helloService;

    NotifyApplication(NotifyHelloService helloService) {
        this.helloService = helloService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info(helloService.status().message());
    }
}
