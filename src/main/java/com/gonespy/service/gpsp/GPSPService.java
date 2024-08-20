package com.gonespy.service.gpsp;

/**
 * Created by gonespy on 8/02/2018.
 *
 * <p>gpsp.gamespy.com:29901
 */
import com.gonespy.service.user.UserManager;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GPSPService implements Runnable {

  private static final String DISPLAY_NAME = GPSPService.class.getSimpleName();
  private static final Logger LOG = LoggerFactory.getLogger(DISPLAY_NAME);

  public static final int GPSP_PORT_NUMBER = 29901;

  private final UserManager userManager;

  public GPSPService(UserManager userManager) {
    this.userManager = userManager;
  }

  @Override
  public void run() {
    try (ServerSocket serverSocket = new ServerSocket(GPSP_PORT_NUMBER)) {
      LOG.info("Listening on port " + GPSP_PORT_NUMBER);
      while (true) {
        Socket clientSocket = serverSocket.accept();
        LOG.info("Accepted new connection from client {}", clientSocket.getRemoteSocketAddress());
        new GPSPServiceThread(userManager, clientSocket).start();
      }
    } catch (IOException e) {
      System.err.println("Could not listen on port " + GPSP_PORT_NUMBER);
      System.exit(-1);
    }
  }
}
