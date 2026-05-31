package dev.unzor.nexus.modules.application.service;

import dev.unzor.nexus.modules.api.dto.ModulesModuleStatus;
import org.springframework.stereotype.Service;

@Service
public class ModulesHelloService {

    public ModulesModuleStatus status() {
        return new ModulesModuleStatus("modules", "UP", "modules module started");
    }
}
