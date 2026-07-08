package io.nexus.example.client;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Instant;

/**
 * Demonstrates the refresh-token flow. The Spring Security OAuth2 client keeps
 * an {@link OAuth2AuthorizedClient} per user; when its access token nears
 * expiry it transparently mints a new one using the persisted refresh token
 * (Nexus issues refresh tokens, see ADR-0016 §5). This page surfaces the current
 * access/refresh token metadata so you can observe the renewal.
 */
@Controller
public class RefreshController {

    @GetMapping("/refresh")
    public String refresh(@RegisteredOAuth2AuthorizedClient("nexus") OAuth2AuthorizedClient client,
                          @AuthenticationPrincipal OidcUser principal, Model model) {
        model.addAttribute("accessTokenValue", client.getAccessToken().getTokenValue());
        model.addAttribute("accessTokenExpiresAt", client.getAccessToken().getExpiresAt());
        model.addAttribute("hasRefreshToken", client.getRefreshToken() != null);
        model.addAttribute("now", Instant.now());
        return "refresh";
    }
}
