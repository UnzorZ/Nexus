package io.nexus.example.client;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

/**
 * Browser home page: shows the claims carried by the logged-in user's ID token
 * (issued by Nexus), including {@code project_id}, {@code authz_version},
 * {@code amr} and the {@code permissions} array (wildcards verbatim).
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home(@AuthenticationPrincipal OidcUser principal, Model model) {
        Map<String, Object> idTokenClaims =
                principal != null && principal.getIdToken() != null
                        ? principal.getIdToken().getClaims()
                        : Map.of();
        OidcUserInfo userInfo = principal != null ? principal.getUserInfo() : null;

        model.addAttribute("claims", idTokenClaims);
        model.addAttribute("permissions", idTokenClaims.get("permissions"));
        model.addAttribute("userinfo", userInfo != null ? userInfo.getClaims() : Map.of());
        return "home";
    }
}
