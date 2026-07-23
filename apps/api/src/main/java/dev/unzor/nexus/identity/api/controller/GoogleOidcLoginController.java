package dev.unzor.nexus.identity.api.controller;

import dev.unzor.nexus.identity.application.context.ProjectAuthenticationContext;
import dev.unzor.nexus.identity.application.service.EndUserLinkBuilder;
import dev.unzor.nexus.identity.application.service.GoogleFederationService;
import dev.unzor.nexus.identity.application.service.GoogleLoginOutcome;
import dev.unzor.nexus.identity.application.service.ProjectSlugResolver;
import dev.unzor.nexus.identity.domain.exception.OidcFederationException;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URL;
import java.util.Map;

/**
 * End-user surface of Google OIDC login ({@code /api/p/{slug}/login/google*}).
 *
 * <p>{@code GET /login/google} redirects the browser to Google with a fresh state+nonce.
 * {@code GET /login/google/callback} is Google's redirect target: it validates the callback,
 * establishes the session (or asks for re-authentication to link), and redirects the browser
 * back to the end-user portal. {@code POST /login/google/link} completes a re-authenticated
 * link and returns the same {@code {redirect}} shape as password login.</p>
 */
@RestController
@RequestMapping("/api/p/{projectSlug}")
class GoogleOidcLoginController {

    private final ProjectSlugResolver slugResolver;
    private final GoogleFederationService federationService;
    private final EndUserLinkBuilder linkBuilder;

    GoogleOidcLoginController(
            ProjectSlugResolver slugResolver,
            GoogleFederationService federationService,
            EndUserLinkBuilder linkBuilder
    ) {
        this.slugResolver = slugResolver;
        this.federationService = federationService;
        this.linkBuilder = linkBuilder;
    }

    record LinkRequest(String password) {
    }

    @GetMapping("/login/google")
    ResponseEntity<Void> begin(
            @PathVariable String projectSlug,
            @RequestParam(name = "continue", required = false) String continueUrl,
            HttpServletRequest request
    ) {
        ProjectAuthenticationContext context = resolve(projectSlug);
        String authorizationUrl;
        try {
            authorizationUrl = federationService.buildAuthorizationUrl(context, continueUrl, request);
        } catch (OidcFederationException exception) {
            return redirect(linkBuilder.googleErrorLink(projectSlug, exception.code()));
        }
        return redirect(authorizationUrl);
    }

    @GetMapping("/login/google/callback")
    ResponseEntity<Void> callback(
            @PathVariable String projectSlug,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String error,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        ProjectAuthenticationContext context = resolve(projectSlug);
        GoogleLoginOutcome outcome = federationService.handleCallback(context, code, state, error, request, response);
        return switch (outcome) {
            case GoogleLoginOutcome.LoggedIn loggedIn ->
                    redirect(resolveSuccessTarget(loggedIn.continueUrl(), projectSlug, request));
            case GoogleLoginOutcome.LinkRequired ignored ->
                    redirect(linkBuilder.googleLinkRequiredLink(projectSlug));
            case GoogleLoginOutcome.FederationError federationError ->
                    redirect(linkBuilder.googleErrorLink(projectSlug, federationError.code()));
        };
    }

    @PostMapping("/login/google/link")
    ResponseEntity<Map<String, String>> link(
            @PathVariable String projectSlug,
            @RequestBody LinkRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        ProjectAuthenticationContext context = resolve(projectSlug);
        GoogleLoginOutcome outcome = federationService.completeLinking(context, request.password(), servletRequest, servletResponse);
        return switch (outcome) {
            case GoogleLoginOutcome.LoggedIn loggedIn -> {
                String redirect = resolveSuccessTarget(loggedIn.continueUrl(), projectSlug, servletRequest);
                yield ResponseEntity.ok(redirect == null ? Map.of() : Map.of("redirect", redirect));
            }
            case GoogleLoginOutcome.LinkRequired ignored ->
                    ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "link_required"));
            case GoogleLoginOutcome.FederationError federationError ->
                    ResponseEntity.status(httpStatus(federationError.code())).body(Map.of("code", federationError.code()));
        };
    }

    private ProjectAuthenticationContext resolve(String projectSlug) {
        try {
            return slugResolver.resolve(projectSlug);
        } catch (ProjectNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
    }

    private ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", location)
                .build();
    }

    private static HttpStatus httpStatus(String code) {
        return "invalid_credentials".equals(code) ? HttpStatus.UNAUTHORIZED
                : "already_linked".equals(code) ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST;
    }

    /**
     * Open-redirect guard for the post-login target: only a relative authorize-resume path of
     * this realm ({@code /p/{slug}/oauth2/...}), or an absolute URL on the API host containing
     * that marker, is honoured. Anything else falls back to the portal.
     */
    private String resolveSuccessTarget(String continueUrl, String projectSlug, HttpServletRequest request) {
        if (!StringUtils.hasText(continueUrl)) {
            return linkBuilder.portalLink(projectSlug);
        }
        String marker = "/p/" + projectSlug + "/oauth2/";
        if (continueUrl.startsWith(marker)) {
            return continueUrl;
        }
        if ((continueUrl.startsWith("http://") || continueUrl.startsWith("https://")) && continueUrl.contains(marker)) {
            try {
                URL url = new URL(continueUrl);
                if (url.getHost() != null && url.getHost().equals(request.getServerName())) {
                    return continueUrl;
                }
            } catch (Exception ignored) {
                return linkBuilder.portalLink(projectSlug);
            }
        }
        return linkBuilder.portalLink(projectSlug);
    }
}
