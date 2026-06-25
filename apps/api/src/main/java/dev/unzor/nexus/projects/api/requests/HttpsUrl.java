package dev.unzor.nexus.projects.api.requests;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valida que el valor (si no es {@code null}) sea una URL absoluta con esquema
 * {@code http} o {@code https}. {@code null} se considera válido para que el
 * campo siga siendo opcional; el valor por defecto se aplica antes de la
 * validación.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = HttpsUrlValidator.class)
public @interface HttpsUrl {

    String message() default "must be a valid absolute http(s) URL";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
