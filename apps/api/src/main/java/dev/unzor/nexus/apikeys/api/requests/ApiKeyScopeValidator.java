package dev.unzor.nexus.apikeys.api.requests;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Valida scopes {@code module:action}: módulo y acción en minúsculas, con
 * guiones/guiones bajos permitidos en la acción. Acepta {@code null} (campo
 * opcional); los nulos en una lista se rechazan con {@code @NotNull}.
 */
public class ApiKeyScopeValidator implements ConstraintValidator<ApiKeyScope, String> {

    private static final Pattern SCOPE = Pattern.compile("^[a-z][a-z0-9-]*:[a-z][a-z0-9_-]*$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return SCOPE.matcher(value).matches();
    }
}
