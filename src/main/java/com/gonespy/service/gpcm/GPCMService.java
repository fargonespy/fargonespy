package com.gonespy.service.gpcm;

/**
 * Created by gonespy on 8/02/2018.
 *
 * gpcm.gamespy.com:29900
 *
 */

import java.io.IOException;
import java.net.ServerSocket;

import com.gonespy.service.user.UserManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GPCMService implements Runnable {

    private static final String DISPLAY_NAME = GPCMService.class.getSimpleName();
    private static final Logger LOG = LoggerFactory.getLogger(DISPLAY_NAME);

    public static final int GPCM_PORT_NUMBER = 29900;

    private final UserManager userManager;

    public GPCMService(UserManager userManager) {
        this.userManager = userManager;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(GPCM_PORT_NUMBER)) {
            LOG.info("Listening on port " + GPCM_PORT_NUMBER);
            while (true) {
                new GPCMServiceThread(userManager, serverSocket.accept()).start();
            }
        } catch (IOException e) {
            System.err.println("Could not listen on port " + GPCM_PORT_NUMBER);
            throw new RuntimeException();
        }
    }
}
