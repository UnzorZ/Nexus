package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.api.dto.IdentityModuleStatus;
import org.springframework.stereotype.Service;

@Service
public class IdentityHelloService {

    public IdentityModuleStatus status() {
        return new IdentityModuleStatus("identity", "UP", "identity module up");
    }
}
