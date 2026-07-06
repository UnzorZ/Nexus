@ApplicationModule(
        displayName = "Identity",
        allowedDependencies = {
                "projects :: Application",
                "projects :: Api",
                "projects :: Exceptions",
                "permissions :: Application",
                "shared :: AuditEvents",
                "shared :: Security",
                "shared :: Validation"
        }
)
package dev.unzor.nexus.identity;

import org.springframework.modulith.ApplicationModule;
