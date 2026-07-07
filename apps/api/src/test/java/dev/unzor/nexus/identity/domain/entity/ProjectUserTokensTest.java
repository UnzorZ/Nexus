package dev.unzor.nexus.identity.domain.entity;

import dev.unzor.nexus.identity.domain.enums.ProjectUserStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the email-verification / password-reset token lifecycle on
 * {@link ProjectUser} (Track A M1 data model): tokens are hashed, single-use (cleared
 * on consume), and verification flips PENDING_VERIFICATION → ACTIVE.
 */
class ProjectUserTokensTest {

    private static ProjectUser newUser() {
        // 4-arg ctor → status PENDING_VERIFICATION.
        return new ProjectUser(UUID.randomUUID(), "alice@example.com", "hash", "Alice");
    }

    @Test
    void issueEmailVerificationStoresHashAndExpiryWithoutChangingStatus() {
        ProjectUser user = newUser();
        Instant expiry = Instant.now().plusSeconds(3600);

        user.issueEmailVerification("abc123hash", expiry);

        assertThat(user.getEmailVerificationTokenHash()).isEqualTo("abc123hash");
        assertThat(user.getEmailVerificationExpiresAt()).isEqualTo(expiry);
        assertThat(user.getStatus()).isEqualTo(ProjectUserStatus.PENDING_VERIFICATION);
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void consumeEmailVerificationVerifiesEmailAndIsSingleUse() {
        ProjectUser user = newUser();
        user.issueEmailVerification("abc123hash", Instant.now().plusSeconds(3600));
        Instant verifiedAt = Instant.now();

        user.consumeEmailVerification(verifiedAt);

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getEmailVerifiedAt()).isEqualTo(verifiedAt);
        assertThat(user.getStatus()).isEqualTo(ProjectUserStatus.ACTIVE);
        // single-use: the token is cleared after consumption (a replay won't match).
        assertThat(user.getEmailVerificationTokenHash()).isNull();
        assertThat(user.getEmailVerificationExpiresAt()).isNull();
    }

    @Test
    void passwordResetTokensAreIssuedAndConsumedSingleUse() {
        ProjectUser user = newUser();
        Instant expiry = Instant.now().plusSeconds(600);

        user.issuePasswordReset("reset-hash", expiry);
        assertThat(user.getPasswordResetTokenHash()).isEqualTo("reset-hash");
        assertThat(user.getPasswordResetExpiresAt()).isEqualTo(expiry);

        user.consumePasswordReset();
        assertThat(user.getPasswordResetTokenHash()).isNull();
        assertThat(user.getPasswordResetExpiresAt()).isNull();
    }
}
