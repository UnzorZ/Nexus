package dev.unzor.nexus.architecture;

import dev.unzor.nexus.NexusApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithArchitectureTests {

    @Test
    void verifiesModulithBoundaries() {
        ApplicationModules.of(NexusApplication.class).verify();
    }
}
