package com.gonespy.service.user;

import java.util.HashMap;
import java.util.Map;

public class UserManager {

  public static class User {
    private long profileID;

    private String nick;
    private boolean online;
    private String status;
    private String location;
    private String address;

    public User(
        long profileID,
        String nick,
        boolean online,
        String status,
        String location,
        String address) {
      this.profileID = profileID;
      this.nick = nick;
      this.online = online;
      this.status = status;
      this.location = location;
      this.address = address;
    }

    public long getProfileID() {
      return profileID;
    }

    public String getNick() {
      return nick;
    }

    public boolean isOnline() {
      return online;
    }

    public String getStatus() {
      return status;
    }

    public String getLocation() {
      return location;
    }

    public String getAddress() {
      return address;
    }
  }

  private Map<String, String> loginUsername = new HashMap<>();
  private Map<Long, User> users = new HashMap<>();

  public synchronized void trackAuthToken(String authToken, String username) {
    this.loginUsername.put(authToken, username);
  }

  public synchronized String getUsernameForAuthToken(String authToken) {
    return loginUsername.get(authToken);
  }

  public synchronized User getUser(long profileId) {
    if (users.containsKey(profileId)) {
      return users.get(profileId);
    }
    return new User(profileId, "Unknown", false, "Offline", "", "");
  }

  public synchronized void registerUser(long profileID, String nick) {
    if (!users.containsKey(profileID)) {
      users.put(profileID, new User(profileID, nick, false, "", "", ""));
    }
  }

  public synchronized User updateStatus(
      long profileID, String nick, boolean online, String status, String location) {
    var user = new User(profileID, nick, online, status, location, "");
    users.put(profileID, user);
    return user;
  }
}
