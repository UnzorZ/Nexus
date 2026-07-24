package dev.unzor.nexus.identity.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Link between a {@link ProjectUser} and an external OIDC identity-provider subject.
 *
 * <p>A link is the durable proof that the owner of a Google identity ({@code sub}) and the
 * owner of a project user account are the same person. Once a link exists, that Google
 * identity logs the user in directly. A link is created <b>only</b> after re-authentication
 * (or as part of provisioning a brand-new account); a verified email match alone never
 * creates a link.</p>
 *
 * <p>Uniqueness is enforced at the database level: one {@code (project_id, provider,
 * subject)} maps to one project user, and one project user has at most one link per
 * provider.</p>
 */
@Entity
@Table(
        name = "project_user_oidc_links",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_oidc_link_project_provider_subject",
                        columnNames = {"project_id", "provider", "subject"}),
                @UniqueConstraint(name = "uk_oidc_link_user_provider",
                        columnNames = {"project_user_id", "provider"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectUserOidcLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "project_user_id", nullable = false)
    private UUID projectUserId;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(length = 320)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ProjectUserOidcLink(UUID projectId, UUID projectUserId, String provider, String subject, String email) {
        this.projectId = Objects.requireNonNull(projectId);
        this.projectUserId = Objects.requireNonNull(projectUserId);
        this.provider = Objects.requireNonNull(provider);
        this.subject = Objects.requireNonNull(subject);
        this.email = email;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
