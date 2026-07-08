package dev.unzor.nexus.shared.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Genera y verifica códigos TOTP (RFC 6238) con HmacSHA1, sin dependencias externas.
 * Es el algoritmo estándar de las apps de autenticador (Google Authenticator, etc.):
 * 6 dígitos, ventana de 30 s.
 *
 * <p>Vive en {@code shared.security} (interfaz nombrada {@code Security}, ya expuesta) para
 * que tanto el portal de usuario final (identity) como el panel (admin) lo reutilicen sin
 * duplicar el algoritmo ni crear una nueva interfaz nombrada de Modulith.</p>
 *
 * <p>Los vectores de test del apéndice B del RFC usan 8 dígitos; por eso
 * {@link #generate(byte[], long, int)} admite un parámetro de dígitos para validar
 * contra ellos, mientras que la API de producción ({@link #DIGITS}) usa 6.</p>
 */
public final class TotpGenerator {

    /** Dígitos de producción (apps de autenticador). */
    public static final int DIGITS = 6;
    /** Ventana de tiempo en segundos. */
    public static final int TIME_STEP_SECONDS = 30;
    /** Tamaño del secret compartido: 160 bits (recomendación RFC 4226 §4). */
    public static final int SECRET_BYTES = 20;

    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpGenerator() {
    }

    /** Genera un secret aleatorio de {@value #SECRET_BYTES} bytes. */
    public static byte[] generateSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        RANDOM.nextBytes(secret);
        return secret;
    }

    /** Código TOTP a 6 dígitos para el instante dado. */
    public static String generate(byte[] secretBytes, long unixTimeSeconds) {
        return generate(secretBytes, unixTimeSeconds, DIGITS);
    }

    /** Código TOTP con un nº de dígitos configurable (para los vectores de 8 del RFC). */
    public static String generate(byte[] secretBytes, long unixTimeSeconds, int digits) {
        long counter = Math.floorDiv(unixTimeSeconds, TIME_STEP_SECONDS);
        byte[] counterBytes = new byte[8];
        for (int i = 7; i >= 0; i--) {
            counterBytes[i] = (byte) (counter & 0xff);
            counter >>>= 8;
        }
        byte[] hmac = hmacSha1(secretBytes, counterBytes);
        int offset = hmac[hmac.length - 1] & 0xf;
        int binary = ((hmac[offset] & 0x7f) << 24)
                | ((hmac[offset + 1] & 0xff) << 16)
                | ((hmac[offset + 2] & 0xff) << 8)
                | (hmac[offset + 3] & 0xff);
        int modulus = (int) Math.pow(10, digits);
        return String.format("%0" + digits + "d", binary % modulus);
    }

    /**
     * Verifica {@code code} (6 dígitos) contra el secret admitiendo una ventana de
     * {@code windowSteps} pasos antes/después para sesgo de reloj. Comparación
     * constant-time.
     */
    public static boolean verify(byte[] secretBytes, String code, long unixTimeSeconds, int windowSteps) {
        if (code == null || code.length() != DIGITS) {
            return false;
        }
        for (int i = -windowSteps; i <= windowSteps; i++) {
            String expected = generate(secretBytes, unixTimeSeconds + (long) i * TIME_STEP_SECONDS);
            if (constantTimeEquals(expected, code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * URI de aprovisionamiento {@code otpauth://totp/...} que las apps escanean como QR.
     */
    public static String provisioningUri(String issuer, String accountName, String base32Secret) {
        String label = enc(issuer + ":" + accountName);
        return "otpauth://totp/" + label
                + "?secret=" + base32Secret
                + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + TIME_STEP_SECONDS;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static byte[] hmacSha1(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA1 not available", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
