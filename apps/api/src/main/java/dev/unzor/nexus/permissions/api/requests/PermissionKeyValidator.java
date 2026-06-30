package dev.unzor.nexus.permissions.api.requests;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Valida claves de permiso positivas (ADR-0003): comodín global {@code *},
 * comodín de espacio de nombres {@code ns.*} o claves exactas de segmentos
 * {@code [a-z0-9_-]+} separados por puntos. Acepta {@code null} para que el
 * campo pueda ser opcional; los nulos en una lista se rechazan a nivel de
 * elemento con {@code @NotNull}.
 */
public class PermissionKeyValidator implements ConstraintValidator<PermissionKey, String> {

    private static final Pattern PERMISSION_KEY = Pattern.compile(
            "^(\\*|[a-z0-9_-]+(\\.[a-z0-9_-]+)*(\\.\\*)?)$"
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return PERMISSION_KEY.matcher(value).matches();
    }
}
