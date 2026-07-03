package dev.unzor.nexus.identity.domain.entity;

import dev.unzor.nexus.identity.domain.StringListConverter;
import dev.unzor.nexus.identity.domain.enums.OauthClientStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Cliente OAuth/OIDC de un proyecto (spec §9.6, §15.3). Pertenece al realm
 * {@code {origin}/p/{slug}} del proyecto. {@code client_id} es globalmente
 * único; {@code client_secret_hash} es {@code {bcrypt}...} para clientes
 * confidenciales y {@code null} para clientes públicos (que deben usar PKCE).
 *
 * <p>El mapeo a {@code RegisteredClient} de Spring Authorization Server lo hace
 * {@code ProjectOauthClientToRegisteredClientMapper}; aquí sólo persistimos el
 * estado crudo del cliente.</p>
 */
@Entity
@Table(
        name = "project_oauth_clients",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_oauth_clients_client_id",
                columnNames = {"client_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectOauthClient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    /** {@code {bcrypt}...} para confidenciales; {@code null} para públicos. */
    @Column(name = "client_secret_hash", length = 200)
    private String clientSecretHash;

    @Column(nullable = false, length = 200)
    private String name;

    @Convert(converter = StringListConverter.class)
    @Column(name = "redirect_uris", nullable = false)
    private List<String> redirectUris;

    @Convert(converter = StringListConverter.class)
    @Column(name = "post_logout_redirect_uris")
    private List<String> postLogoutRedirectUris;

    @Convert(converter = StringListConverter.class)
    @Column(name = "grant_types", nullable = false)
    private List<String> grantTypes;

    @Convert(converter = StringListConverter.class)
    @Column(name = "scopes", nullable = false)
    private List<String> scopes;

    @Column(name = "require_pkce", nullable = false)
    private boolean requirePkce;

    @Column(name = "consent_required", nullable = false)
    private boolean consentRequired;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OauthClientStatus status;

    @Column(name = "created_by_account_id")
    private UUID createdByAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProjectOauthClient(
            UUID projectId,
            String clientId,
            String clientSecretHash,
            String name,
            List<String> redirectUris,
            List<String> postLogoutRedirectUris,
            List<String> grantTypes,
            List<String> scopes,
            boolean requirePkce,
            boolean consentRequired,
            UUID createdByAccountId
    ) {
        this.projectId = Objects.requireNonNull(projectId);
        this.clientId = Objects.requireNonNull(clientId);
        this.clientSecretHash = clientSecretHash; // nullable para públicos
        this.name = Objects.requireNonNull(name);
        this.redirectUris = List.copyOf(redirectUris);
        this.postLogoutRedirectUris = postLogoutRedirectUris == null ? List.of() : List.copyOf(postLogoutRedirectUris);
        this.grantTypes = List.copyOf(grantTypes);
        this.scopes = List.copyOf(scopes);
        this.requirePkce = requirePkce;
        this.consentRequired = consentRequired;
        this.createdByAccountId = createdByAccountId;
        this.status = OauthClientStatus.ACTIVE;
    }

    /** Confidencial = tiene secreto; público = sin secreto (debe usar PKCE). */
    public boolean isConfidential() {
        return clientSecretHash != null;
    }

    public void rename(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public void updateRedirectUris(List<String> redirectUris, List<String> postLogoutRedirectUris) {
        this.redirectUris = List.copyOf(redirectUris);
        this.postLogoutRedirectUris = postLogoutRedirectUris == null ? List.of() : List.copyOf(postLogoutRedirectUris);
    }

    public void updateScopes(List<String> scopes) {
        this.scopes = List.copyOf(scopes);
    }

    /** Rotación del secreto: reemplaza el hash y reactiva el cliente. */
    public void rotateSecret(String clientSecretHash) {
        this.clientSecretHash = Objects.requireNonNull(clientSecretHash);
        this.status = OauthClientStatus.ACTIVE;
    }

    public void disable() {
        this.status = OauthClientStatus.DISABLED;
    }

    public void enable() {
        this.status = OauthClientStatus.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
