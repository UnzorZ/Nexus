package dev.unzor.nexus.apikeys.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara el scope ({@code module:action}) que requiere un endpoint del API de
 * proyecto ({@code /api/v1/**}). El interceptor de scopes lo comprueba contra
 * los scopes de la API key resuelta; si falta, responde {@code 403 missing_scope}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiredScope {

    String value();
}
