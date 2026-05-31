package dev.unzor.nexus.apikeys.application.service;

import dev.unzor.nexus.apikeys.api.dto.ApiKeysModuleStatus;
import org.springframework.stereotype.Service;

@Service
public class ApiKeysHelloService {

    public ApiKeysModuleStatus status() {
        return new ApiKeysModuleStatus("apikeys", "UP", "apikeys module started");
    }
}
