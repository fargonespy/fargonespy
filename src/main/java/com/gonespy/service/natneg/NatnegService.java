package com.gonespy.service.natneg;

import com.gonespy.service.availability.AvailabilityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.HexFormat;

public class NatnegService implements Runnable{
    private static final String DISPLAY_NAME = NatnegService.class.getSimpleName();
    private static final Logger LOG = LoggerFactory.getLogger(DISPLAY_NAME);
    public static final int PORT = 27901;

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
                LOG.info("natneg packet: {}", HexFormat.ofDelimiter(" ").formatHex(packet.getData(), 0, packet.getLength()));
            } catch (IOException e) {
                LOG.error("Could not handle packet", e);
                loop = false;
            }
        }
        socket.close();
    }
}
