package dev.unzor.nexus.projects.api.requests;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valida que el rol asignable mediante invitación no sea {@code OWNER}. La
 * propiedad de un proyecto se gestiona mediante cambio de rol, no mediante
 * invitación directa.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NonOwnerRoleValidator.class)
public @interface NonOwnerRole {

    String message() default "must not be OWNER";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
