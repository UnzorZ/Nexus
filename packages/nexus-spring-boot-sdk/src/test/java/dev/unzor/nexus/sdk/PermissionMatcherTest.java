package dev.unzor.nexus.sdk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobertura del glob-match (spec §14.3): exacto, comodín de namespace
 * ({@code orders.*} cubre uno o más segmentos pero no el namespace pelado),
 * comodín global {@code *}, y casos negativos.
 */
class PermissionMatcherTest {

    @Test
    void exactMatch() {
        assertThat(PermissionMatcher.matches(List.of("orders.read"), "orders.read")).isTrue();
    }

    @Test
    void namespaceWildcardMatchesOneSegment() {
        assertThat(PermissionMatcher.matches(List.of("orders.*"), "orders.cancel")).isTrue();
    }

    @Test
    void namespaceWildcardMatchesDeeper() {
        assertThat(PermissionMatcher.matches(List.of("orders.*"), "orders.billing.export")).isTrue();
    }

    @Test
    void globalWildcardMatchesEverything() {
        assertThat(PermissionMatcher.matches(List.of("*"), "anything.at.all")).isTrue();
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
    void emptyOrNullInputsReturnFalse() {
        assertThat(PermissionMatcher.matches(List.of(), "orders.read")).isFalse();
        assertThat(PermissionMatcher.matches(null, "orders.read")).isFalse();
        assertThat(PermissionMatcher.matches(List.of("orders.read"), "")).isFalse();
        assertThat(PermissionMatcher.matches(List.of("orders.read"), null)).isFalse();
    }

    @Test
    void anyGrantSatisfies() {
        assertThat(PermissionMatcher.matches(List.of("users.read", "orders.*"), "orders.read")).isTrue();
    }
}
