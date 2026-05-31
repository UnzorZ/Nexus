package dev.unzor.nexus.admin.application.service;

import dev.unzor.nexus.admin.api.dto.AdminModuleStatus;
import org.springframework.stereotype.Service;

@Service
public class AdminHelloService {

    public AdminModuleStatus status() {
        return new AdminModuleStatus("admin", "UP", "admin module started");
    }
}
