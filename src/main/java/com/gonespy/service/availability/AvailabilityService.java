package com.gonespy.service.availability;

import com.gonespy.service.crypt.Crypter;
import com.gonespy.service.serverlist.Server;
import com.gonespy.service.serverlist.ServerManager;
import com.gonespy.service.util.PacketUtils;
import com.gonespy.service.util.StringUtils;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AvailabilityService implements Runnable {
  private static final String DISPLAY_NAME = AvailabilityService.class.getSimpleName();
  private static final Logger LOG = LoggerFactory.getLogger(DISPLAY_NAME);
  public static final int PORT = 27900;

  private final byte[] AVAILABLE_RESPONSE =
      new byte[] {(byte) 0xFE, (byte) 0xFD, 0x09, 0x00, 0x00, 0x00, 0x00};

  private static final int AVAILABILITY_CHECK = 9;
  private static final int HEARTBEAT = 3;

  private static final int CHALLENGE = 1;

  private static final HexFormat hex = HexFormat.of();
  private static final HexFormat prettyHex = HexFormat.ofDelimiter(" ");

  public static void main(String[] args) {
    new AvailabilityService().run();
  }

  private final ServerManager serverManager;

  public AvailabilityService(ServerManager serverManager) {
    this.serverManager = serverManager;
  }

  private AvailabilityService() {
    this(new ServerManager());
  }

  private static class Session {
    private final String game;
    private final Server server;
    String challenge;

    public Session(String game, Server server, String challenge) {
      this.game = game;
      this.server = server;
      this.challenge = challenge;
    }

    public synchronized boolean isChallengePending() {
      return !challenge.isEmpty();
    }

    public String getGame() {
      return game;
    }

    public Server getServer() {
      return server;
    }

    public synchronized String getChallenge() {
      return challenge;
    }

    public synchronized void challengeAccepted() {
      challenge = "";
    }
  }

  private Map<Long, Session> sessions = new ConcurrentHashMap<>();

  private void rc4Encrypt(byte[] key, byte[] data) {
    if (key.length == 0) {
      throw new IllegalArgumentException("key is not set");
    }

    var s = new byte[256];
    for (int i = 0; i < s.length; i++) {
      s[i] = (byte) (i & 0xff);
    }

    int j = 0;
    for (int i = 0; i < 256; i++) {
      // Get index to swap with
      j = (j + s[i] + key[i % key.length]) & 0xff;

      // Perform swap.
      var tmp = s[j];
      s[j] = s[i];
      s[i] = tmp;
    }

    // Pseudo-random generation algorithm + encryption
    int i = 0;
    j = 0;
    for (int x = 0; x < data.length; x++) {
      var val = data[x];
      // Modified RC4?
      i = (i + 1 + val) & 0xff;
      j = (j + s[i]) & 0xff;

      var tmp = s[j];
      s[j] = s[i];
      s[i] = tmp;

      data[x] ^= s[(s[i] + s[j]) & 0xff];
    }
  }

  private byte[] prepareRC4Base64(byte[] key, byte[] data) {
    rc4Encrypt(key, data);

    var paddedData = new byte[data.length + 1];
    System.arraycopy(data, 0, paddedData, 0, data.length);

    var out = Base64.getEncoder().encode(paddedData);
    var nd = new byte[out.length + 1];
    System.arraycopy(out, 0, nd, 0, out.length);
    return nd;
  }

  private void handlePacket(DatagramSocket socket, DatagramPacket incomingPacket)
      throws IOException {
    var data = incomingPacket.getData();
    var peer = incomingPacket.getSocketAddress().toString();
    LOG.info("[{}] Packet type {}", peer, data[0]);
    LOG.info(
        "[{}] Packet dump: {} ", peer, prettyHex.formatHex(data, 0, incomingPacket.getLength()));
    if (data[0] == AVAILABILITY_CHECK) {
      LOG.info("[{}] Availability check", peer);
      var bb = ByteBuffer.wrap(data, 0, incomingPacket.getLength());
      bb.position(5);
      var game = PacketUtils.readZeroTerminatedString(bb);
      LOG.info("[{}] Availability check for game {}", peer, game);
      final byte[] buf = AVAILABLE_RESPONSE;
      socket.send(
          new DatagramPacket(
              buf, buf.length, incomingPacket.getAddress(), incomingPacket.getPort()));
      return;
    } else if (data[0] == CHALLENGE) {
      LOG.info("[{}] Challenge", peer);

      var bb = ByteBuffer.wrap(data);
      bb.order(ByteOrder.LITTLE_ENDIAN);
      bb.position(1);
      long sid = bb.getInt() & 0xffffffffl;
      LOG.info("[{}] Challenge sid {}", peer, sid);

      var session = this.sessions.get(sid);
      if (session == null) {
        LOG.info("[{}] Session {} not found", peer, sid);
        return;
      }

      var cb = session.getChallenge().getBytes(StandardCharsets.UTF_8);
      var ourChallenge = prepareRC4Base64(Crypter.getKey(session.getGame()), cb);
      var clientChallenge = new byte[incomingPacket.getLength() - 5];

      bb.get(clientChallenge);
      LOG.info(
          "[{}] Challenge received from client {} vs our challenge {}",
          peer,
          prettyHex.formatHex(clientChallenge),
          prettyHex.formatHex(ourChallenge));
      if (!Arrays.equals(clientChallenge, ourChallenge)) {
        LOG.info("[{}] Challenge did not match", peer);
        sessions.remove(sid);
      } else {
        LOG.info("[{}] Challenge accepted, adding server", peer);
        session.challengeAccepted();
        this.serverManager.updateServer(session.game, sid, session.server);
        var out = ByteBuffer.allocate(1024);
        out.put((byte) 0xFE);
        out.put((byte) 0xFD);
        out.put((byte) 0x0A);
        var pb = new byte[out.position()];
        out.rewind();
        out.get(pb);
        LOG.info("[{}] Sending challenge response: {}", peer, prettyHex.formatHex(pb));
      }
    } else if (data[0] == HEARTBEAT) {
      LOG.info("[{}] Heartbeat", peer);
      var bb = ByteBuffer.wrap(data);
      bb.order(ByteOrder.LITTLE_ENDIAN);
      bb.position(1);
      long sid = bb.getInt() & 0xffffffffl;
      LOG.info("[{}] Heartbeat sid {}", peer, sid);
      var s = new String(Arrays.copyOfRange(data, 5, data.length), StandardCharsets.UTF_8);
      var pairs = s.split("\\u0000");
      var attrs = new HashMap<String, String>();
      for (int i = 0; i < pairs.length; i += 2) {
        var key = pairs[i];
        if (i + 1 < pairs.length) {
          var value = pairs[i + 1];
          LOG.info("[{}] attr {}: {}", peer, key, value);
          attrs.put(key, value);
        }
      }
      //            attrs.put("hostname", "9.9.9.9");

      var game = attrs.get("gamename");
      if (game == null) {
        LOG.warn("Game name missing from attributes");
        return;
      }

      var server = new Server(incomingPacket.getAddress(), (short) incomingPacket.getPort(), attrs);
      var session = this.sessions.get(sid);
      if (session == null || session.isChallengePending()) {
        var challenge = StringUtils.randomString(6) + "00";
        challenge += hex.formatHex(incomingPacket.getAddress().getAddress());
        challenge += hex.toHexDigits((short) incomingPacket.getPort());
        LOG.info("[{}] challenge string: {}", peer, challenge);

        session = new Session(game, server, challenge);

        var out = ByteBuffer.allocate(1024);
        out.put((byte) 0xFE);
        out.put((byte) 0xFD);
        out.put((byte) 0x01);
        out.order(ByteOrder.LITTLE_ENDIAN);
        out.putInt((int) sid);
        out.put(challenge.getBytes(StandardCharsets.UTF_8));
        out.put((byte) 0);

        var pb = new byte[out.position()];
        out.rewind();
        out.get(pb);
        LOG.info("[{}] sending challenge request {} ", peer, prettyHex.formatHex(pb));

        this.sessions.put(sid, session);
        socket.send(
            new DatagramPacket(
                pb, pb.length, incomingPacket.getAddress(), incomingPacket.getPort()));
        return;
      }
      var sc = attrs.get("statechanged");
      if (sc != null && sc.equals("2")) {
        LOG.info("[{}] deleting sid {}", peer, sid);
        this.serverManager.deleteServer(game, sid);
      } else {
        this.serverManager.updateServer(game, sid, server);
      }
    }
  }

  @Override
  public void run() {
    DatagramSocket socket = null;
    try {
      socket = new DatagramSocket(PORT);
      LOG.info("Listening on port " + PORT + "[UDP]");
    } catch (SocketException e) {
      LOG.error("Could not open socket on port " + PORT + ". " + e.getMessage());
      System.exit(1);
    }

    boolean loop = true;
    while (loop) {
      try {
        byte[] buffer = new byte[2048];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        handlePacket(socket, packet);
      } catch (IOException e) {
        LOG.error("Could not handle packet", e);
        loop = false;
      }
    }
    socket.close();
  }
}
