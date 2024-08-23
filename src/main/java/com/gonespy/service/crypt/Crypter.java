package com.gonespy.service.crypt;

import com.gonespy.service.gpcm.GPCMServiceThread;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// based on
// https://github.com/AdmiralCurtiss/nintendo_dwc_emulator/blob/master/gamespy/gs_utility.py
public class Crypter {
  private static final Logger LOG = LoggerFactory.getLogger(GPCMServiceThread.class);

  public static byte[] getKey(String game) {
    // Taken from https://github.com/AdmiralCurtiss/nintendo_dwc_emulator/blob/master/gslist.cfg
    String key =
        switch (game) {
          case "ut3ps3" -> "nT2Mtz";
          case "dundefndps3" -> "B1UcDx";
          case "50centsandps3" -> "ORydHB";
          default -> "";
        };
    if (key.isEmpty()) {
      LOG.info("No key available for game {}", game);
      key = "zzzzzz";
    }
    return key.getBytes(StandardCharsets.UTF_8);
  }

  public static byte[] encrypt(byte[] key, byte[] validate, byte[] data) {
    // Add room for the header.
    var tmpLen = 20;
    var newData = new byte[data.length + tmpLen];
    System.arraycopy(data, 0, newData, tmpLen, data.length);
    data = newData;

    var rnd = ~System.currentTimeMillis();

    for (int i = 0; i < tmpLen; i++) {
      rnd = (rnd * 0x343FD) + 0x269EC3;
      data[i] = (byte) ((rnd ^ key[i % key.length] ^ validate[i % validate.length]) & 0xff);
    }

    var headerLen = 7;
    data[0] = (byte) ((headerLen - 2) ^ 0xec);
    data[1] = 0x00;
    data[2] = 0x00;
    data[headerLen - 1] = (byte) ((tmpLen - headerLen) ^ 0xea);

    // The header of the data gets chopped off in init(), so save it
    var header = new byte[tmpLen];
    System.arraycopy(data, 0, header, 0, tmpLen);
    var encxkey = new char[261];
    data = init(encxkey, key, validate, data);
    func6e(encxkey, data, data.length);

    // Reappend header that we saved earlier before returning to make
    // the complete buffer
    var out = new byte[header.length + data.length];
    System.arraycopy(header, 0, out, 0, header.length);
    System.arraycopy(data, 0, out, header.length, data.length);
    return out;
  }

  public static byte[] decrypt(byte[] key, byte[] validate, byte[] data) {
    var encxkey = new char[261];
    data = init(encxkey, key, validate, data);
    func6(encxkey, data, data.length);
    return data;
  }

  private static byte[] init(char[] encxkey, byte[] key, byte[] validate, byte[] data) {
    if (data.length < 1) {
      throw new IllegalArgumentException("no data");
    }

    int header_len = ((data[0] ^ 0xec) + 2) & 0xff;
    if (data.length < header_len) {
      throw new IllegalArgumentException("invalid data length");
    }

    int data_start = (data[header_len - 1] ^ 0xea) & 0xff;
    if (data.length < (header_len + data_start)) {
      throw new IllegalArgumentException("invalid data length (data start)");
    }

    var sub = new byte[data.length - header_len];
    System.arraycopy(data, header_len, sub, 0, data.length - header_len);
    enctypex_funcx(encxkey, key.clone(), validate.clone(), sub, data_start);

    var out = new byte[sub.length - data_start];
    System.arraycopy(sub, data_start, out, 0, sub.length - data_start);
    return out;
  }

  private static void enctypex_funcx(
      char[] encxkey, byte[] key, byte[] validate, byte[] data, int dataLen) {
    for (int i = 0; i < dataLen; i++) {
      validate[(key[i % key.length] * i) & 7] ^= validate[i & 7] ^ data[i];
    }

    func4(encxkey, validate, 8);
  }

  private static void func4(char[] encxkey, byte[] id, int idLen) {
    if (idLen < 1) {
      throw new IllegalArgumentException("invalid idLen");
    }

    for (int i = 0; i < 256; i++) {
      encxkey[i] = (char) i;
    }

    var n1 = 0;
    var n2 = 0;
    for (int i = 255; i >= 0; i--) {
      var rv = func5(encxkey, i, id, idLen, n1, n2);
      var t1 = rv.t1;
      n1 = rv.n1;
      n2 = rv.n2;
      var t2 = encxkey[i];
      encxkey[i] = encxkey[t1];
      encxkey[t1] = t2;
    }

    encxkey[256] = encxkey[1];
    encxkey[257] = encxkey[3];
    encxkey[258] = encxkey[5];
    encxkey[259] = encxkey[7];
    encxkey[260] = encxkey[n1 & 0xff];
  }

  static class func5ret {
    int t1;
    int n1;
    int n2;

    public func5ret(int t1, int n1, int n2) {
      this.t1 = t1;
      this.n1 = n1;
      this.n2 = n2;
    }
  }

  private static func5ret func5(char[] encxkey, int cnt, byte[] id, int idLen, int n1, int n2) {
    if (cnt == 0) {
      return new func5ret(0, n1, n2);
    }

    var mask = 1;
    var doLoop = true;
    if (cnt > 1) {
      while (doLoop) {
        mask = (mask << 1) + 1;
        doLoop = mask < cnt;
      }
    }

    var i = 0;
    var tmp = 0;
    doLoop = true;
    while (doLoop) {
      n1 = encxkey[n1 & 0xff] + id[n2];
      n2 += 1;

      if (n2 >= idLen) {
        n2 = 0;
        n1 += idLen;
      }

      tmp = n1 & mask;

      i += 1;
      if (i > 11) {
        tmp %= cnt;
      }

      doLoop = tmp > cnt;
    }

    return new func5ret(tmp, n1, n2);
  }

  private static void func6e(char[] encxkey, byte[] data, int dataLen) {
    for (int i = 0; i < dataLen; i++) {
      data[i] = func7e(encxkey, data[i]);
    }
  }

  private static void func6(char[] encxkey, byte[] data, int dataLen) {
    for (int i = 0; i < dataLen; i++) {
      data[i] = func7(encxkey, data[i]);
    }
  }

  private static byte func7(char[] encxkey, byte d) {
    var a = encxkey[256];
    var b = encxkey[257];
    var c = encxkey[a];
    encxkey[256] = (char) ((a + 1) & 0xff);
    encxkey[257] = (char) ((b + c) & 0xff);

    a = encxkey[260];
    b = encxkey[257];
    b = encxkey[b];
    c = encxkey[a];
    encxkey[a] = b;

    a = encxkey[259];
    b = encxkey[257];
    a = encxkey[a];
    encxkey[b] = a;

    a = encxkey[256];
    b = encxkey[259];
    a = encxkey[a];
    encxkey[b] = a;

    a = encxkey[256];
    encxkey[a] = c;

    b = encxkey[258];
    a = encxkey[c];
    c = encxkey[259];
    b = (char) ((a + b) & 0xff);
    encxkey[258] = b;

    a = b;
    c = encxkey[c];
    b = encxkey[257];
    b = encxkey[b];
    a = encxkey[a];
    c = (char) ((b + c) & 0xff);
    b = encxkey[260];
    b = encxkey[b];
    c = (char) ((b + c) & 0xff);
    b = encxkey[c];
    c = encxkey[256];
    c = encxkey[c];
    a = (char) ((a + c) & 0xff);
    c = encxkey[b];
    b = encxkey[a];
    encxkey[260] = (char) (d & 0xff);

    c = (char) ((c ^ b ^ d) & 0xff);
    encxkey[259] = c;

    return (byte) c;
  }

  private static byte func7e(char[] encxkey, byte d) {
    char a = encxkey[256];
    char b = encxkey[257];
    char c = encxkey[a];
    encxkey[256] = (char) ((a + 1) & 0xff);
    encxkey[257] = (char) ((b + c) & 0xff);

    a = encxkey[260];
    b = encxkey[257];
    b = encxkey[b];
    c = encxkey[a];
    encxkey[a] = b;

    a = encxkey[259];
    b = encxkey[257];
    a = encxkey[a];
    encxkey[b] = a;

    a = encxkey[256];
    b = encxkey[259];
    a = encxkey[a];
    encxkey[b] = a;

    a = encxkey[256];
    encxkey[a] = c;

    b = encxkey[258];
    a = encxkey[c];
    c = encxkey[259];
    b = (char) ((a + b) & 0xff);
    encxkey[258] = b;

    a = b;
    c = encxkey[c];
    b = encxkey[257];
    b = encxkey[b];
    a = encxkey[a];
    c = (char) ((b + c) & 0xff);
    b = encxkey[260];
    b = encxkey[b];
    c = (char) ((b + c) & 0xff);
    b = encxkey[c];
    c = encxkey[256];
    c = encxkey[c];
    a = (char) ((a + c) & 0xff);
    c = encxkey[b];
    b = encxkey[a];
    c = (char) ((c ^ b ^ d) & 0xff);
    encxkey[260] = c;
    encxkey[259] = (char) (d & 0xff);

    return (byte) c;
  }

  public static void main(String[] args) {
    var data = "Hello world".getBytes(StandardCharsets.UTF_8);
    var key = "nT2Mtz".getBytes(StandardCharsets.UTF_8);
    var validate = "abcefghi".getBytes(StandardCharsets.UTF_8);
    data = encrypt(key, validate, data);
    data = decrypt(key, validate, data);
    System.out.println(new String(data));
  }
}
