package dev.unzor.nexus.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Fuerza los atributos {@code SameSite} y {@code Secure} de la cookie CSRF que emite
 * {@link org.springframework.security.web.csrf.CookieCsrfTokenRepository}.
 *
 * <p>En Spring Security 7 / Servlet 6 la cookie CSRF se escribe mediante
 * {@code response.addCookie(Cookie)} siempre que el contenedor sea Servlet 6+, y
 * {@code jakarta.servlet.http.Cookie} no expone {@code SameSite}: por tanto un valor
 * {@code sameSite=None} configurado a través de {@code setCookieCustomizer} se pierde.
 * Este filtro envuelve la respuesta para que, cuando se añade la cookie
 * {@code XSRF-TOKEN}, se reemita como una cabecera {@code Set-Cookie} con los atributos
 * {@code sameSite}/{@code secure} aplicados. Así la cookie CSRF se almacena también en
 * flujos <em>cross-site</em> (p. ej. túneles HTTPS de {@code remote-dev}), de forma
 * consistente con la cookie de sesión, cuya configuración
 * ({@code nexus.session.cookie.same-site} / {@code .secure}) reutiliza.</p>
 *
 * <p>El filtro se coloca antes que {@link org.springframework.security.web.csrf.CsrfFilter}
 * para que el envoltorio esté activo cuando el repositorio guarda el token.</p>
 */
public final class SameSiteCsrfCookieFilter extends OncePerRequestFilter {

    private static final String SET_COOKIE = "Set-Cookie";

    private final String cookieName;
    private final String sameSite;
    private final boolean secure;

    public SameSiteCsrfCookieFilter(String cookieName, String sameSite, boolean secure) {
        this.cookieName = cookieName;
        this.sameSite = (sameSite == null) ? "" : sameSite.trim();
        this.secure = secure;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        filterChain.doFilter(request, new SameSiteCsrfResponse(response));
    }

    private final class SameSiteCsrfResponse extends HttpServletResponseWrapper {

        SameSiteCsrfResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void addCookie(Cookie cookie) {
            if (cookie != null && cookieName.equals(cookie.getName())) {
                addHeader(SET_COOKIE, render(cookie));
            } else {
                super.addCookie(cookie);
            }
        }

        @Override
        public void addHeader(String name, String value) {
            super.addHeader(name, rewrite(name, value));
        }

        private String rewrite(String name, String value) {
            if (value == null || !SET_COOKIE.equalsIgnoreCase(name) || !targetsCookie(value)) {
                return value;
            }
            String rewritten = value;
            if (secure && !hasAttribute(rewritten, "Secure")) {
                rewritten = rewritten + "; Secure";
            }
            if (!sameSite.isEmpty() && !hasAttribute(rewritten, "SameSite")) {
                rewritten = rewritten + "; SameSite=" + sameSite;
            }
            return rewritten;
        }

        private boolean targetsCookie(String headerValue) {
            int assign = headerValue.indexOf('=');
            if (assign < 0) {
                return false;
            }
            String name = headerValue.substring(0, assign).trim();
            return name.equalsIgnoreCase(cookieName);
        }

        private String render(Cookie cookie) {
            StringBuilder sb = new StringBuilder();
            sb.append(cookie.getName()).append('=').append(encode(cookie.getValue()));
            String path = cookie.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            sb.append("; Path=").append(path);
            if (secure || cookie.getSecure()) {
                sb.append("; Secure");
            }
            if (!sameSite.isEmpty()) {
                sb.append("; SameSite=").append(sameSite);
            }
            return sb.toString();
        }

        private boolean hasAttribute(String headerValue, String attribute) {
            for (String segment : headerValue.split(";")) {
                String trimmed = segment.trim();
                if (trimmed.regionMatches(true, 0, attribute, 0, attribute.length())) {
                    return true;
                }
            }
            return false;
        }
    }

    private static String encode(String value) {
        return (value == null) ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
