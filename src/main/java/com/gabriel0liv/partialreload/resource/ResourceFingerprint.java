package com.gabriel0liv.partialreload.resource;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

public record ResourceFingerprint(String algorithm, String hash, long size) {
    public static final String SHA_256 = "SHA-256";

    public ResourceFingerprint {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(hash, "hash");
        if (!SHA_256.equals(algorithm)) {
            throw new IllegalArgumentException("Unsupported fingerprint algorithm: " + algorithm);
        }
        hash = hash.toLowerCase(Locale.ROOT);
        if (!hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("SHA-256 hash must contain 64 hexadecimal characters");
        }
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
    }

    public static ResourceFingerprint sha256(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        MessageDigest digest = newDigest();
        return new ResourceFingerprint(SHA_256, HexFormat.of().formatHex(digest.digest(bytes)), bytes.length);
    }

    public static ResourceFingerprint sha256(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[8192];
        long size = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read > 0) {
                digest.update(buffer, 0, read);
                size += read;
            }
        }
        return new ResourceFingerprint(SHA_256, HexFormat.of().formatHex(digest.digest()), size);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java runtime does not provide SHA-256", exception);
        }
    }
}
