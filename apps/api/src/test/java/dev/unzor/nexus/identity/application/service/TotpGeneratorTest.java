package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.shared.security.Base32;
import dev.unzor.nexus.shared.security.TotpGenerator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida {@link TotpGenerator} contra los vectores del apéndice B del RFC 6238 (HmacSHA1,
 * 8 dígitos) y el round-trip de {@link Base32}.
 */
class TotpGeneratorTest {

    /** Seed ASCII "12345678901234567890" (20 bytes) del apéndice B del RFC 6238. */
    private static final byte[] RFC_SEED = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesRfc6238Vectors() {
        // (unixTimeSeconds, expected 8-digit code) — apéndice B del RFC 6238.
        assertThat(TotpGenerator.generate(RFC_SEED, 59L, 8)).isEqualTo("94287082");
        assertThat(TotpGenerator.generate(RFC_SEED, 1111111109L, 8)).isEqualTo("07081804");
        assertThat(TotpGenerator.generate(RFC_SEED, 1111111111L, 8)).isEqualTo("14050471");
        assertThat(TotpGenerator.generate(RFC_SEED, 1234567890L, 8)).isEqualTo("89005924");
        assertThat(TotpGenerator.generate(RFC_SEED, 2000000000L, 8)).isEqualTo("69279037");
        assertThat(TotpGenerator.generate(RFC_SEED, 20000000000L, 8)).isEqualTo("65353130");
    }

    @Test
    void verifyAcceptsCurrentAndAdjacentWindow() {
        byte[] secret = TotpGenerator.generateSecret();
        long now = 1_700_000_000L;
        String code = TotpGenerator.generate(secret, now);

        assertThat(TotpGenerator.verify(secret, code, now, 1)).isTrue();
        // Un paso anterior y el siguiente también validan dentro de la ventana.
        assertThat(TotpGenerator.verify(secret, code, now - TotpGenerator.TIME_STEP_SECONDS, 1)).isTrue();
        assertThat(TotpGenerator.verify(secret, code, now + TotpGenerator.TIME_STEP_SECONDS, 1)).isTrue();
        // Fuera de la ventana (2 pasos) ya no.
        assertThat(TotpGenerator.verify(secret, code, now - 2L * TotpGenerator.TIME_STEP_SECONDS, 1)).isFalse();
    }

    @Test
    void verifyRejectsWrongCode() {
        byte[] secret = TotpGenerator.generateSecret();
        assertThat(TotpGenerator.verify(secret, "000000", 1_700_000_000L, 1)).isFalse();
        assertThat(TotpGenerator.verify(secret, "12345", 1_700_000_000L, 1)).isFalse();
        assertThat(TotpGenerator.verify(secret, null, 1_700_000_000L, 1)).isFalse();
    }

    @Test
    void provisioningUriCarriesSecretAndIssuer() {
        String uri = TotpGenerator.provisioningUri("Acme Inc", "alice@acme.test", "JBSWY3DPEHPK3PXP");
        assertThat(uri).startsWith("otpauth://totp/Acme%20Inc%3Aalice%40acme.test?");
        assertThat(uri).contains("secret=JBSWY3DPEHPK3PXP");
        assertThat(uri).contains("digits=6");
        assertThat(uri).contains("period=30");
    }

    @Test
    void base32RoundTripsArbitraryBytes() {
        byte[] secret = TotpGenerator.generateSecret();
        String encoded = Base32.encode(secret);
        assertThat(Base32.decode(encoded)).isEqualTo(secret);
    }

    @Test
    void base32RoundTripsKnownVector() {
        // "fooba" -> MZXW6YTB (RFC 4648 §10), "foob" -> MZXW6YQ.
        assertThat(Base32.encode("foob".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YQ");
        assertThat(Base32.encode("fooba".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTB");
        assertThat(new String(Base32.decode("MZXW6YTB"), StandardCharsets.US_ASCII)).isEqualTo("fooba");
    }
}
