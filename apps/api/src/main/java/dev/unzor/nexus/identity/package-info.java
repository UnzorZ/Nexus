@ApplicationModule(
        displayName = "Identity",
        allowedDependencies = {
                "projects :: Application",
                "projects :: Api",
                "shared :: Validation"
        }
)
package dev.unzor.nexus.identity;

import org.springframework.modulith.ApplicationModule;
