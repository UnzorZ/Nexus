package dev.unzor.nexus.audit.application.service;

import dev.unzor.nexus.audit.api.dto.AuditModuleStatus;
import org.springframework.stereotype.Service;

@Service
public class AuditHelloService {

    public AuditModuleStatus status() {
        return new AuditModuleStatus("audit", "UP", "audit module started");
    }
}
