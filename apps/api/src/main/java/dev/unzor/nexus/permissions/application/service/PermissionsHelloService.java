package dev.unzor.nexus.permissions.application.service;

import dev.unzor.nexus.permissions.api.dto.PermissionsModuleStatus;
import org.springframework.stereotype.Service;

@Service
public class PermissionsHelloService {

    public PermissionsModuleStatus status() {
        return new PermissionsModuleStatus("permissions", "UP", "permissions module started");
    }
}
