package dev.unzor.nexus.identity.application.service;

/**
 * Codificación Base32 (RFC 4648) sin padding, mayúsculas — el formato en el que las
 * apps de autenticador esperan el secret TOTP. Sin dependencias externas.
 */
public final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int[] DECODE_TABLE = buildDecodeTable();

    private Base32() {
    }

    public static String encode(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1f;
                out.append(ALPHABET.charAt(index));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1f;
            out.append(ALPHABET.charAt(index));
        }
        return out.toString();
    }

    public static byte[] decode(String value) {
        String clean = value == null ? "" : value.replaceAll("[\\s=]", "").toUpperCase();
        if (clean.isEmpty()) {
            return new byte[0];
        }
        byte[] out = new byte[clean.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;
        for (int i = 0; i < clean.length(); i++) {
            int c = DECODE_TABLE[clean.charAt(i)];
            if (c < 0) {
                throw new IllegalArgumentException("Invalid Base32 character: " + clean.charAt(i));
            }
            buffer = (buffer << 5) | c;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return out;
    }

    private static int[] buildDecodeTable() {
        int[] table = new int[128];
        for (int i = 0; i < 128; i++) {
            table[i] = -1;
        }
        for (int i = 0; i < ALPHABET.length(); i++) {
            table[ALPHABET.charAt(i)] = i;
        }
        return table;
    }
}
