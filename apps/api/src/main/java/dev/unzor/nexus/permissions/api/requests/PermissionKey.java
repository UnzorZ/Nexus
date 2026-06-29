package dev.unzor.nexus.permissions.api.requests;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valida que el valor sea una clave de permiso válida: el comodín global
 * {@code *}, un comodín de espacio de nombres {@code orders.*}, o una clave
 * exacta de segmentos separados por puntos {@code orders.cancel},
 * {@code inventory.stock.read}. Cada segmento es {@code [a-z0-9_-]+} o
 * {@code *}. Se aplica también a cada elemento de una colección
 * ({@code List<@PermissionKey String>}).
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PermissionKeyValidator.class)
public @interface PermissionKey {

    String message() default "must be a valid permission key (e.g. orders.cancel, orders.*, *)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
