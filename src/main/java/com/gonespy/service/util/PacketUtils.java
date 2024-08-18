package com.gonespy.service.util;

import java.nio.ByteBuffer;

public class PacketUtils {
    public static String readZeroTerminatedString(ByteBuffer bb) {
        var sb = new StringBuilder();
        while (true) {
            var b = bb.get();
            if (b == 0) {
                break;
            }
            sb.append((char) (b & 0xff));
        }
        return sb.toString();
    }
}
