package com.gonespy.service.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GPMessageReader {
  private static final Logger LOG = LoggerFactory.getLogger(GPMessageReader.class);

  private static char MESSAGE_DELIMITER = '\\';
  private final InputStream is;

  public static class Message {
    private String command;
    private String commandValue;
    private Map<String, String> params;

    public Message(String command, String commandValue, Map<String, String> params) {
      this.command = command;
      this.commandValue = commandValue;
      this.params = params;
    }

    public String getCommand() {
      return command;
    }

    public String getCommandValue() {
      return commandValue;
    }

    public Map<String, String> getParams() {
      return params;
    }
  }

  private StringBuilder msg = new StringBuilder();
  private String command;
  private String commandValue;
  private String key;
  private String value;
  private Map<String, String> params;

  public GPMessageReader(InputStream is) {
    this.is = is;
  }

  public Message read() throws IOException {
    var readMore = false;
    while (true) {
      if (this.msg.isEmpty()) {
        readMore = true;
      }
      if (readMore) {
        byte[] buffer = new byte[1024];
        var n = this.is.read(buffer);
        if (n == -1) {
          return null;
        }
        this.msg.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
      }

      LOG.info("current buffer: [" + msg + "] length " + msg.length());

      if (msg.charAt(0) != MESSAGE_DELIMITER) {
        throw new RuntimeException("expected delimeter, got " + (int) msg.charAt(0));
      }
      int valIdx = 1;
      for (var i = 1; i < msg.length(); i++) {
        if (msg.charAt(i) == MESSAGE_DELIMITER) {
          var data = msg.substring(valIdx, i);
          valIdx = i + 1;
          if (data.equals("final")) {
            if (command == null) {
              throw new RuntimeException("null command");
            } else if (params == null) {
              params = new HashMap<>();
            }
            var newMsg = new StringBuilder();
            newMsg.append(msg.substring(i + 1));
            msg = newMsg;
            var rv = new Message(command, commandValue, params);
            this.command = null;
            this.commandValue = null;
            this.params = null;
            return rv;
          }
          if (command == null) {
            command = data;
          } else if (commandValue == null) {
            commandValue = data;
          } else if (key == null) {
            key = data;
          } else if (value == null) {
            value = data;
            if (this.params == null) {
              this.params = new HashMap<>();
            }
            this.params.put(key, value);
            key = null;
            value = null;
          }
        }
      }
      readMore = true;
      command = null;
      commandValue = null;
      key = null;
      value = null;
      params = null;
    }
  }

  public static void main(String[] args) throws IOException {
    var r = new GPMessageReader(System.in);
    while (true) {
      var msg = r.read();
      if (msg == null) {
        System.out.println("done");
        return;
      }
      System.out.println(msg.command);
      System.out.println(msg.params);
    }
  }
}
