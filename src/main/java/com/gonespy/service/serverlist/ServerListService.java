package com.gonespy.service.serverlist;

import com.gonespy.service.availability.AvailabilityService;
import com.gonespy.service.crypt.Crypter;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerListService implements Runnable {
  private static final String DISPLAY_NAME = ServerListService.class.getSimpleName();
  private static final int SERVERLIST_PORT = 28910;

  private static final Logger LOG = LoggerFactory.getLogger(DISPLAY_NAME);

  private static final HexFormat hex = HexFormat.ofDelimiter(" ");

  private static final int UNSOLICITED_UDP_FLAG = 1;
  private static final int PRIVATE_IP_FLAG = 2;
  private static final int CONNECT_NEGOTIATE_FLAG = 4;
  private static final int ICMP_IP_FLAG = 8;
  private static final int NONSTANDARD_PORT_FLAG = 16;
  private static final int NONSTANDARD_PRIVATE_PORT_FLAG = 32;
  private static final int HAS_KEYS_FLAG = 64;
  private static final int HAS_FULL_RULES_FLAG = 128;

  private final ServerManager serverManager;

  public ServerListService(ServerManager serverManager) {
    this.serverManager = serverManager;
  }

  private ServerListService() {
    this(new ServerManager());
  }

  public static void main(String[] args) {
    var sm = new ServerManager();
    var as = new AvailabilityService(sm);
    var sls = new ServerListService(sm);

    new Thread(as).start();
    new Thread(sls).start();
  }

  // ProcessMainListData

  @Override
  public void run() {
    ServerSocket server = null;
    try {
      server = new ServerSocket(SERVERLIST_PORT);
      LOG.info("Listening on port " + SERVERLIST_PORT + "[TCP]");
    } catch (IOException e) {
      LOG.error("Could not open socket on port " + SERVERLIST_PORT + ". " + e.getMessage());
      System.exit(1);
    }

    boolean loop = true;
    while (loop) {
      try {
        Socket socket = server.accept();
        Thread thread = new ListThread(serverManager, "ListThread", socket);
        thread.start();
      } catch (IOException e) {
        LOG.error("Could not spawn thread to handle connection", e);
        loop = false;
      }
    }
    try {
      server.close();
    } catch (IOException e) {
      LOG.warn("could not close socket", e);
    }
  }

  public static class ListThread extends Thread {
    private final ServerManager serverManager;

    private final Socket socket;

    public ListThread(ServerManager serverManager, String name, Socket socket) {
      super(name);
      this.serverManager = serverManager;
      this.socket = socket;
    }

    private static String readString(ByteBuffer bb) {
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

    private static void writeField(ByteBuffer bb, String value) {
      // 0xff means is it's a null terminated string, otherwise it's an index
      // into "popular values".
      bb.put((byte) 0xff);
      bb.put(value.getBytes(StandardCharsets.UTF_8));
      bb.put((byte) 0);
    }

    private void appendServer(
        ByteBuffer out, Server server, String[] fields, boolean natNeg, boolean unsolicitedUDP) {
      int flags = HAS_KEYS_FLAG | NONSTANDARD_PORT_FLAG | ICMP_IP_FLAG;
      //            if (server.getAttr("natneg") != null) {
      //                flags |= CONNECT_NEGOTIATE_FLAG;
      //            }
      if (natNeg) {
        flags |= CONNECT_NEGOTIATE_FLAG;
      }
      if (unsolicitedUDP) {
        flags |= UNSOLICITED_UDP_FLAG;
      }
      if (server.getAttr("localip0") != null) {
        flags |= PRIVATE_IP_FLAG;
      }
      if (server.getAttr("localport") != null) {
        flags |= NONSTANDARD_PRIVATE_PORT_FLAG;
      }

      out.put((byte) (flags & 0xff));

      LOG.info(
          "server address {}:{} (natneg {}, private IP {}, private port {})",
          server.getAddress(),
          server.getPort(),
          (flags & CONNECT_NEGOTIATE_FLAG) != 0,
          (flags & PRIVATE_IP_FLAG) != 0,
          (flags & NONSTANDARD_PRIVATE_PORT_FLAG) != 0);
      var addr = server.getAddress().getAddress();

      // public IP
      var publicIP = server.getAttr("publicip");
      if (publicIP != null) {
        var ipParts = publicIP.split("\\.");
        if (ipParts.length != 4) {
          LOG.warn("incorrect number of IP octets in {}", publicIP);
        }
        for (var o : ipParts) {
          out.put((byte) (Integer.parseInt(o) & 0xFF));
        }
      } else {
        LOG.info("filling in public IP using IP {}...", server.getAddress().getHostAddress());
        if (addr.length != 4) {
          LOG.warn("incorrect number of IP octets in {}", server.getAddress().getHostAddress());
        }
        out.put(addr);
      }
      var publicPort = server.getAttr("publicport");
      if (publicPort != null) {
        publicPort = server.getAttr("localport");
      }
      if (publicPort == null) {
        LOG.warn("Server missing port information...");
        publicPort = "0";
      }
      out.putShort((short) (Integer.parseInt(publicPort) & 0xFFFF));

      if ((flags & PRIVATE_IP_FLAG) != 0) {
        String ip = server.getAttr("localip0");
        var ipParts = ip.split("\\.");
        if (ipParts.length != 4) {
          LOG.warn("incorrect number of IP octets in {}", ip);
        }
        for (var o : ipParts) {
          out.put((byte) (Integer.parseInt(o) & 0xFF));
        }
      }
      if ((flags & NONSTANDARD_PRIVATE_PORT_FLAG) != 0) {
        var port = (short) (Integer.parseInt(server.getAttr("localport")) & 0xFFFF);
        out.putShort(port);
      }

      // icmp ip
      out.put(addr[0]);
      out.put(addr[1]);
      out.put(addr[2]);
      out.put(addr[3]);

      // write server fields
      out.put((byte) 0xff);

      for (var field : fields) {
        var val = server.getAttr(field);
        if (field.equals("mapname")) {
          var name = server.getAddress().getHostAddress();
          if (natNeg) {
            name += "[NN]";
          }
          if (unsolicitedUDP) {
            name += "[UP]";
          }
          val = val.replaceAll("OwningPlayerName=BulletstormPlayer", "OwningPlayerName=" + name);
        }
        LOG.info("attr {}={}", field, val);
        writeField(out, val);
      }
    }

    private void handlePacket(ByteBuffer bb) throws IOException {
      LOG.info("Got list request from " + socket.getRemoteSocketAddress());
      var len = bb.getShort();
      LOG.info("specified length " + len + " vs packet length " + bb.limit());
      if (len != bb.limit()) {
        LOG.warn("invalid length");
        return;
      }

      var req = bb.get();
      if (req != 0) {
        LOG.warn("unhandled request type " + req);
        return;
      }

      var protoVersion = bb.get();
      if (protoVersion != 1) {
        LOG.warn("unexpected protocol version " + protoVersion);
        return;
      }

      var listEncodingVersion = bb.get();
      if (listEncodingVersion != 3) {
        LOG.warn("unexpected list encoding version " + protoVersion);
        return;
      }

      // ignore
      var gameVersion = bb.getInt();
      LOG.info("game version: " + gameVersion);

      var forGame = readString(bb);
      LOG.info("for game " + forGame);

      var fromGame = readString(bb);
      LOG.info("from game " + fromGame);

      var challenge = new byte[8];
      bb.get(challenge);
      LOG.info("challenge " + hex.formatHex(challenge));

      var filter = readString(bb);
      LOG.info("filter: " + filter);

      var fieldsSpec = readString(bb);
      if (!fieldsSpec.isEmpty() && fieldsSpec.charAt(0) == '\\') {
        fieldsSpec = fieldsSpec.substring(1);
      }

      var fields = fieldsSpec.split("\\\\");
      LOG.info("fields: " + Arrays.asList(fields));

      var options = bb.getInt();
      LOG.info("options: " + options);

      if ((options & 0x80) != 0) {
        bb.order(ByteOrder.LITTLE_ENDIAN);
        var limit = bb.getInt();
        bb.order(ByteOrder.BIG_ENDIAN);
        LOG.info("query limit: " + limit);
      }

      var out = ByteBuffer.allocate(2048);

      // fixed header length, client public IP + port (6 bytes)
      //                out.put((byte) 192);
      //                out.put((byte) 168);
      //                out.put((byte) 100);
      //                out.put((byte) 1);
      var ia = ((InetSocketAddress) socket.getRemoteSocketAddress());
      var remoteAddr = ia.getAddress().getAddress();
      out.put(remoteAddr[0]);
      out.put(remoteAddr[1]);
      out.put(remoteAddr[2]);
      out.put(remoteAddr[3]);
      // weird, why does the client need to know its own source port??
      out.putShort((short) ia.getPort());

      out.put((byte) fields.length);
      for (var field : fields) {
        out.put((byte) 0); // KEYTYPE_STRING
        out.put(field.getBytes(StandardCharsets.UTF_8));
        out.put((byte) 0);
      }

      // no "popular values"
      out.put((byte) 0);

      var servers = this.serverManager.listServers(forGame);
      LOG.info("found " + servers.size() + " servers");

      // size = 5 + 2 (NONSTANDARD_PORT_FLAG) + 4 (ICMP_IP_FLAG) = 11

      for (var server : servers) {
        appendServer(out, server, fields, false, false);
        //                appendServer(out, server, fields, true, false);
        //                appendServer(out, server, fields, false, true);
      }

      out.put((byte) 0);
      out.putInt(0xffffffff);

      var pb = new byte[out.position()];
      out.rewind();
      out.get(pb);
      LOG.info("response: " + hex.formatHex(pb));

      var key = Crypter.getKey(forGame);
      pb = Crypter.encrypt(key, challenge, pb);

      socket.getOutputStream().write(pb);
    }

    public void run() {
      try {
        var is = socket.getInputStream();
        var buf = new byte[1024];
        while (true) {
          var n = is.read(buf);
          if (n == -1) {
            break;
          }
          LOG.info("read {} bytes", n);
          var bb = ByteBuffer.wrap(buf, 0, n);
          handlePacket(bb);
        }
      } catch (IOException e) {
        LOG.error("Failed to communicate with peer " + socket.getRemoteSocketAddress(), e);
      } finally {
        try {
          socket.close();
        } catch (IOException e) {
          LOG.warn("could not close socket", e);
        }
      }
    }
  }
}
