package com.gonespy.service.serverlist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerManager {

  private static class GameServers {
    private Map<Long, Server> servers = new HashMap<>();

    public synchronized void update(long sessionID, Server server) {
      servers.put(sessionID, server);
    }

    public synchronized List<Server> all() {
      return new ArrayList<>(servers.values());
    }

    public synchronized void delete(long sid) {
      this.servers.remove(sid);
    }

    public synchronized Server get(long sid) {
      return servers.get(sid);
    }
  }

  private Map<String, GameServers> servers = new HashMap<>();

  public synchronized void updateServer(String game, long sessionID, Server server) {
    if (!servers.containsKey(game)) {
      servers.put(game, new GameServers());
    }
    servers.get(game).update(sessionID, server);
  }

  public synchronized void deleteServer(String game, long sid) {
    if (!servers.containsKey(game)) {
      return;
    }

    servers.get(game).delete(sid);
  }

  public synchronized List<Server> listServers(String game) {
    if (!servers.containsKey(game)) {
      return new ArrayList<>();
    }
    return servers.get(game).all();
  }
}
