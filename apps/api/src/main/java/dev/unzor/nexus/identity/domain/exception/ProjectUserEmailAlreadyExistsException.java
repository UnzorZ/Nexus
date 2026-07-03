package dev.unzor.nexus.identity.domain.exception;

import java.util.UUID;

/**
 * Se lanza al intentar crear un {@code ProjectUser} cuyo email ya existe dentro
 * del mismo proyecto (restricción {@code uk_project_user_project_email}). El
 * mismo email sí puede existir en otro proyecto: son identidades distintas.
 */
public class ProjectUserEmailAlreadyExistsException extends RuntimeException {

    private final String email;

    public ProjectUserEmailAlreadyExistsException(UUID projectId, String email) {
        super("Project user already exists: projectId=" + projectId + ", email=" + email);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
