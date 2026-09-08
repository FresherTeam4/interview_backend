package com.baseProject.myBaseProject.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Sha256 {
    private static final String ALGORITHM = "SHA-256";

    private Sha256() {
    }

    public static String hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance(ALGORITHM).digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(ALGORITHM + " is unavailable", exception);
        }
    }
}
