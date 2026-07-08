package io.nexus.example.client;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PermissionMatcher} — the Nexus §14.3 glob rules
 * (exact, namespace wildcard, global wildcard).
 */
class PermissionMatcherTest {

    @Test
    void exactMatch() {
        assertThat(PermissionMatcher.matches(List.of("orders.read"), "orders.read")).isTrue();
    }

    @Test
    void namespaceWildcardMatchesOneSegment() {
        assertThat(PermissionMatcher.matches(List.of("orders.*"), "orders.read")).isTrue();
        assertThat(PermissionMatcher.matches(List.of("orders.*"), "orders.cancel")).isTrue();
    }

    @Test
    void namespaceWildcardMatchesDeeperSegments() {
        // "orders.*" covers anything under the "orders." namespace (prefix match).
        assertThat(PermissionMatcher.matches(List.of("orders.*"), "orders.billing.export")).isTrue();
    }

    @Test
    void globalWildcardMatchesEverything() {
        assertThat(PermissionMatcher.matches(List.of("*"), "orders.read")).isTrue();
        assertThat(PermissionMatcher.matches(List.of("*"), "inventory.stock.read")).isTrue();
    }

    @Test
    void namespaceWildcardDoesNotMatchBareNamespace() {
        assertThat(PermissionMatcher.matches(List.of("orders.*"), "orders")).isFalse();
    }

    @Test
    void unrelatedKeyDoesNotMatch() {
        assertThat(PermissionMatcher.matches(List.of("users.read"), "orders.read")).isFalse();
        assertThat(PermissionMatcher.matches(List.of("orders.read"), "orders.write")).isFalse();
    }

    @Test
    void emptyOrNullInputsAreFalse() {
        assertThat(PermissionMatcher.matches(List.of(), "orders.read")).isFalse();
        assertThat(PermissionMatcher.matches(null, "orders.read")).isFalse();
        assertThat(PermissionMatcher.matches(Set.of("orders.*"), "")).isFalse();
        assertThat(PermissionMatcher.matches(Set.of("orders.*"), null)).isFalse();
    }

    @Test
    void anyGrantSatisfiesIsEnough() {
        assertThat(PermissionMatcher.matches(List.of("users.read", "orders.*"), "orders.read")).isTrue();
    }
}
