package dev.unzor.nexus.architecture;

import dev.unzor.nexus.admin.persistence.repository.NexusAccountRepository;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.projects.persistence.repository.ProjectMembershipRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryIsolationTests {

    @Test
    void nexusAccountsMayExposeGlobalJpaOperations() {
        assertThat(JpaRepository.class).isAssignableFrom(NexusAccountRepository.class);
    }

    @Test
    void projectScopedRepositoriesDoNotExposeGlobalFindById() {
        assertThat(JpaRepository.class.isAssignableFrom(ProjectMembershipRepository.class))
                .isFalse();
        assertThat(JpaRepository.class.isAssignableFrom(ProjectUserRepository.class))
                .isFalse();

        assertThat(hasMethodNamed(ProjectMembershipRepository.class, "findById")).isFalse();
        assertThat(hasMethodNamed(ProjectUserRepository.class, "findById")).isFalse();
    }

    private static boolean hasMethodNamed(Class<?> type, String methodName) {
        return Arrays.stream(type.getMethods())
                .anyMatch(method -> method.getName().equals(methodName));
    }
}
