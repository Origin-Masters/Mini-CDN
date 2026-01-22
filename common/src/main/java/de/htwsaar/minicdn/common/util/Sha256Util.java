package de.htwsaar.minicdn.common.util;

import java.security.MessageDigest;

public final class Sha256Util {

    private Sha256Util() {}

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            return toHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute SHA-256", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
