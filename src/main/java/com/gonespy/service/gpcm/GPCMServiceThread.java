package com.gonespy.service.gpcm;

/** Created by gonespy on 8/02/2018. */
import static com.gonespy.service.util.GPMessageUtils.*;

import com.gonespy.service.shared.Constants;
import com.gonespy.service.user.UserManager;
import com.gonespy.service.util.GPMessageReader;
import com.gonespy.service.util.GPNetworkException;
import com.gonespy.service.util.StringUtils;
import com.gonespy.service.util.UserUtils;
import com.google.common.base.Strings;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GPCMServiceThread extends Thread {
  private static final Logger LOG = LoggerFactory.getLogger(GPCMServiceThread.class);

  public static final int INT_AUTH_LENGTH = 4;
  private static final String DUMMY_SERVER_CHALLENGE = "ZXX7h9eiTe0EP5teW1yiajFqY5URTykw";
  private static final String DUMMY_SESSION_KEY = Strings.padEnd("", INT_AUTH_LENGTH, '5');
  public static final String DUMMY_USER_ID = Strings.padEnd("", INT_AUTH_LENGTH, '6');
  public static final String DUMMY_PROFILE_ID = Strings.padEnd("", INT_AUTH_LENGTH, '7');
  private static final String DUMMY_LOGIN_TOKEN = "XdR2LlH69XYzk3KCPYDkTY__";
  public static final String DUMMY_UNIQUE_NICK = "BulletstormPlayer";

  private static Map<Long, GPCMServiceThread> handles = new HashMap<>();
  private static Map<Long, Set<Long>> subscribers = new HashMap<>();

  private final UserManager userManager;

  private final Socket socket;

  private final OutputStream os;

  private long profileID;
  private String nick;

  private Set<Long> buddies = new HashSet<>();

  public GPCMServiceThread(UserManager userManager, Socket socket) throws IOException {
    super("GPSPServiceThread");
    this.userManager = userManager;
    this.socket = socket;
    this.os = socket.getOutputStream();
  }

  private synchronized void send(String reply) throws IOException {
    LOG.info("sending: {}", reply);
    byte[] data = reply.getBytes(StandardCharsets.UTF_8);
    os.write(data);
    os.flush();
  }

  private void sendUserStatus(UserManager.User user) throws IOException {
    var buddyStatus = new LinkedHashMap<String, String>();
    buddyStatus.put("bm", "100");
    buddyStatus.put("f", Long.toString(user.getProfileID()));
    String msg;
    if (user.isOnline()) {
      msg =
          String.format(
              "|s|%s|ss|%s|ls|%s|ip|%d|p|0|qm|0",
              "2", // status
              user.getStatus(),
              user.getLocation(),
              33686018 // get_ip_as_int
              );
    } else {
      msg = "|s|0|ss|Offline";
    }
    buddyStatus.put("msg", msg);
    send(createGPMessage(buddyStatus));
  }

  // additional calls seen:

  // UT3
  // \addbuddy\\sesskey\5555\newprofileid\0\reason\PS3 Buddy
  // Sync\final\\addbuddy\\sesskey\5555\newprofileid\0\reason\PS3 Buddy Sync\final\

  private void handleAddBuddy(Map<String, String> params) throws IOException {
    var profileID = Long.parseLong(params.get("newprofileid"));
    var user = this.userManager.getUser(profileID);
    sendUserStatus(user);
    buddies.add(profileID);
    synchronized (GPCMServiceThread.class) {
      if (!subscribers.containsKey(profileID)) {
        subscribers.put(profileID, new HashSet<>());
      }
      subscribers.get(profileID).add(this.profileID);
    }
  }

  private void handleGetProfile(Map<String, String> params) throws IOException {
    var profileID = Long.parseLong(params.get("profileid"));
    var user = this.userManager.getUser(profileID);

    var profile = new LinkedHashMap<String, String>();
    profile.put("pi", "");
    profile.put("profileid", Long.toString(profileID));
    profile.put("nick", user.getNick());
    profile.put("userid", Long.toString(profileID) + 1);
    profile.put("email", "foo@bar.com");
    profile.put("sig", "abc123");
    profile.put("uniquenick", user.getNick());
    profile.put("pid", "11");
    profile.put("lon", "0.000000");
    profile.put("lat", "0.000000");
    profile.put("loc", "");
    profile.put("id", params.get("id"));
    send(createGPMessage(profile));
  }

  public void run() {
    try (InputStream is = socket.getInputStream(); ) {
      Map<String, String> responseDataMap = new LinkedHashMap<>();
      responseDataMap.put("lc", "1");
      responseDataMap.put("challenge", DUMMY_SERVER_CHALLENGE);
      responseDataMap.put("id", "1");
      send(createGPMessage(responseDataMap));

      var msgReader = new GPMessageReader(is);
      var peer = socket.getRemoteSocketAddress();

      while (true) {
        var msg = msgReader.read();
        if (msg == null) {
          LOG.info("[{}] Disconnecting.", peer);
          break;
        }

        LOG.info(
            "[{}] Message from client: {}({}) {}",
            peer,
            msg.getCommand(),
            msg.getCommandValue(),
            msg.getParams());

        switch (msg.getCommand()) {
          case "login" -> handleLogin(msg.getParams());
          case "getprofile" -> handleGetProfile(msg.getParams());
          case "updatepro" -> handleUpdateProfile(msg.getParams());
          case "status" -> handleStatus(msg);
          case "ka" -> handleKeepAlive(msg.getParams());
          case "addbuddy" -> handleAddBuddy(msg.getParams());
          default -> LOG.info("[{}] unimplemented command '{}'", peer, msg.getCommand());
        }
      }

    } catch (GPNetworkException e) {
      LOG.info("Client closed connection");
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      try {
        LOG.info("Closing socket");
        synchronized (GPCMServiceThread.class) {
          if (profileID != 0) {
            handles.remove(profileID);
            for (var b : buddies) {
              subscribers.get(b).remove(profileID);
            }
          }
        }
        socket.close();
      } catch (IOException e) {
        LOG.info("Could not close socket. Oh well..");
      }
    }
  }

  private void handleLogin(Map<String, String> params) throws IOException {
    // request should look like:
    // \login\\challenge\l0OtMmxlm2pPSy5ra4lynHSMB2Qbci7M\authtoken\1111\partnerid\19\response\d3e1370bf11b79770e2dc6b36cecfb6a\port\6500\productid\12999\gamename\bstormps3\namespaceid\28\sdkrevision\59\quiet\0\id\1\final\
    // response should look like:
    // \blk\0\list\\final\\bdy\0\list\\final\\lc\2\sesskey\55555555555555555555555555555555\
    // userid\66666666666666666666666666666666\profileid\77777777777777777777777777777777\lt\XdR2LlH69XYzk3KCPYDkTY__\proof\10504cc226cc97f1d15f8c3269407500\id\1\final\
    final String authToken = params.get("authtoken"); // user = authtoken for PS3 preauth
    var nick = this.userManager.getUsernameForAuthToken(authToken);
    if (nick == null) {
      LOG.info("could not determine username for auth token {}", authToken);
      nick = "UnknownPlayer";
    }

    final String clientChallenge = params.get("challenge");

    // block list - empty
    var blockList = createGPEmptyListMessage("blk");
    send(blockList);

    // buddy list - empty
    var bdyData = createGPEmptyListMessage("bdy");
    send(bdyData);

    // login data
    Map<String, String> responseDataMap = new LinkedHashMap<>();
    responseDataMap.put("lc", "2"); // int
    responseDataMap.put("sesskey", DUMMY_SESSION_KEY); // int
    responseDataMap.put("userid", DUMMY_USER_ID); // int
    var id = UserUtils.profileID(nick);
    profileID = id;
    this.nick = nick;
    responseDataMap.put("profileid", Long.toString(id)); // int
    responseDataMap.put("uniquenick", nick);
    responseDataMap.put("lt", DUMMY_LOGIN_TOKEN); // string // login token
    // password = partnerChallenge for PS3 preauth
    responseDataMap.put(
        "proof",
        StringUtils.gsLoginProof(
            Constants.DUMMY_PARTNER_CHALLENGE, authToken, clientChallenge, DUMMY_SERVER_CHALLENGE));
    responseDataMap.put("id", "1"); // int

    synchronized (GPCMServiceThread.class) {
      handles.put(profileID, this);
    }

    send(createGPMessage(responseDataMap));
  }

  private void handleUpdateProfile(Map<String, String> params) throws IOException {
    // \ updatepro\\sesskey\5555\publicmask\0\partnerid\19\final\

    /*// update profile, setting publicmask=0 ? maybe making the profile invisible to the rest of
    // the gamespy network because it is a shadow PS account?
    // try sending back current user's info
    Map<String, String> loginResponseData = new LinkedHashMap<>();
    loginResponseData.put("pi", ""); // int
    loginResponseData.put("profileid", Strings.padEnd("", INT_AUTH_LENGTH, '7')); // int
    loginResponseData.put("nick", "someguy");
    loginResponseData.put("uniquenick", "someguy");
    loginResponseData.put("sig", "xxx"); // don't know what this is
    String loginData = createGPMessage(loginResponseData);
    out.print(loginData);*/
  }

  private void handleKeepAlive(Map<String, String> params) throws IOException {
    // \ka\\final\
    // keep-alive - just send it back (client will ignore)
    Map<String, String> responseData = new LinkedHashMap<>();
    responseData.put("ka", "");
    send(createGPMessage(responseData));
  }

  private void handleStatus(GPMessageReader.Message msg) throws IOException {
    boolean online = msg.getCommandValue().equals("2");
    String status = msg.getParams().get("statstring");
    String location = msg.getParams().get("locstring");
    var user = this.userManager.updateStatus(this.profileID, nick, online, status, location);
    synchronized (GPCMServiceThread.class) {
      var subs = subscribers.get(profileID);
      if (subs != null) {
        for (var subProfileID : subs) {
          var handle = handles.get(subProfileID);
          if (handle != null) {
            LOG.info("Broadcasting user status {} -> {}", this.profileID, subProfileID);
            handle.sendUserStatus(user);
          }
        }
      }
    }
  }
}
