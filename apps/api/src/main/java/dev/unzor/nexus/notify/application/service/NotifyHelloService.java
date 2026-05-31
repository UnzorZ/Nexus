package dev.unzor.nexus.notify.application.service;

import dev.unzor.nexus.notify.api.dto.NotifyModuleStatus;
import org.springframework.stereotype.Service;

@Service
public class NotifyHelloService {

    public NotifyModuleStatus status() {
        return new NotifyModuleStatus("notify", "UP", "notify module started");
    }
}
