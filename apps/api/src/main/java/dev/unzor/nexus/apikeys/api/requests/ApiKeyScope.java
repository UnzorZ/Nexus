package dev.unzor.nexus.apikeys.api.requests;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valida que el valor sea un scope de API key con formato {@code module:action}
 * (p. ej. {@code registry:heartbeat}, {@code permissions:declare}). Se aplica
 * también a cada elemento de una colección ({@code List<@ApiKeyScope String>}).
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ApiKeyScopeValidator.class)
public @interface ApiKeyScope {

    String message() default "must be a valid scope (module:action, e.g. registry:heartbeat)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
