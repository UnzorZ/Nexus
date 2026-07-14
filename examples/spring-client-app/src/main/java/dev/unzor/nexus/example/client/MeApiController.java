package dev.unzor.nexus.example.client;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Returns the access-token claims as Nexus issued them ({@code sub},
 * {@code project_id}, {@code authz_version}, {@code permissions}, scopes …).
 * Handy for inspecting what the resource server actually sees after validating
 * the JWT locally.
 */
@RestController
@RequestMapping("/api/me")
public class MeApiController {

    @GetMapping
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        return jwt.getClaims();
    }
}
