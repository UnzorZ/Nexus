package dev.unzor.nexus.apikeys.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyHasherTests {

    private final ApiKeyHasher hasher = new ApiKeyHasher();

    @Test
    void generatedKeyHasFormatPrefixAndHash() {
        ApiKeyHasher.GeneratedKey generated = hasher.generate("my-shop");

        assertThat(generated.fullKey()).startsWith("nxs_my-shop_");
        assertThat(generated.keyPrefix()).hasSize(ApiKeyHasher.PREFIX_LENGTH);
        assertThat(generated.keyHash()).hasSize(64); // SHA-256 hex
        assertThat(generated.fullKey()).doesNotContain(generated.keyHash());
    }

    @Test
    void verifyAcceptsTheOriginalKey() {
        ApiKeyHasher.GeneratedKey generated = hasher.generate("shop");
        assertThat(hasher.verify(generated.fullKey(), generated.keyHash())).isTrue();
    }

    @Test
    void verifyRejectsTamperedAndUnrelatedKeys() {
        ApiKeyHasher.GeneratedKey generated = hasher.generate("shop");
        assertThat(hasher.verify(generated.fullKey() + "tamper", generated.keyHash())).isFalse();
        assertThat(hasher.verify("nxs_other_aaaa", generated.keyHash())).isFalse();
        assertThat(hasher.verify(null, generated.keyHash())).isFalse();
    }

    @Test
    void prefixOfExtractsTheStoredPrefix() {
        ApiKeyHasher.GeneratedKey generated = hasher.generate("shop");
        assertThat(hasher.prefixOf(generated.fullKey())).isEqualTo(generated.keyPrefix());
    }

    @Test
    void prefixOfIsNullForMalformedKeys() {
        assertThat(hasher.prefixOf("garbage")).isNull();
        assertThat(hasher.prefixOf("nxs_onlyonepart")).isNull();
        assertThat(hasher.prefixOf(null)).isNull();
    }

    @Test
    void twoGeneratedKeysDiffer() {
        assertThat(hasher.generate("shop").fullKey())
                .isNotEqualTo(hasher.generate("shop").fullKey());
    }
}
