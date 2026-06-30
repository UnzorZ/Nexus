package dev.unzor.nexus.projects.api.requests;

import dev.unzor.nexus.projects.domain.enums.ProjectMembershipRole;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Rechaza el rol {@code OWNER}; acepta {@code null} (la nulidad se valida aparte
 * con {@code @NotNull}).
 */
public class NonOwnerRoleValidator implements ConstraintValidator<NonOwnerRole, ProjectMembershipRole> {

    @Override
    public boolean isValid(ProjectMembershipRole role, ConstraintValidatorContext context) {
        if (role == null) {
            return true;
        }
        return role != ProjectMembershipRole.OWNER;
    }
}
