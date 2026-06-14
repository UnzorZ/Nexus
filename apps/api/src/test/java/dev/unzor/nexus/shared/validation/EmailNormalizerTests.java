package dev.unzor.nexus.shared.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailNormalizerTests {

    @Test
    void trimsAndLowercasesEmail() {
        assertThat(EmailNormalizer.normalize("  Owner@Example.COM  "))
                .isEqualTo("owner@example.com");
    }

    @Test
    void returnsNullForNullInput() {
        assertThat(EmailNormalizer.normalize(null)).isNull();
    }
}
