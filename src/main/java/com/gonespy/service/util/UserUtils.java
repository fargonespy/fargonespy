package com.gonespy.service.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class UserUtils {
    public static long profileID(String nick) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        byte[] hash = digest.digest(nick.getBytes(StandardCharsets.UTF_8));
        hash[hash.length - 4] &= 0x7f;

        var bb = ByteBuffer.wrap(hash, hash.length - 4, 4);
        var pid = Integer.toUnsignedLong(bb.getInt());
        return pid;
    }

}
