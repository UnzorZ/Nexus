package dev.unzor.nexus.registry.application.service;

import dev.unzor.nexus.registry.api.dto.RegistryModuleStatus;
import org.springframework.stereotype.Service;

@Service
public class RegistryHelloService {

    public RegistryModuleStatus status() {
        return new RegistryModuleStatus("registry", "UP", "registry module started");
    }
}
