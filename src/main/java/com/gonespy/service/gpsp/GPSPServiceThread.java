package com.gonespy.service.gpsp;

/**
 * Created by gonespy on 8/02/2018.
 *
 */

import com.gonespy.service.user.UserManager;
import com.gonespy.service.util.GPMessageUtils;
import com.gonespy.service.shared.GPState;
import com.gonespy.service.util.UserUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class GPSPServiceThread extends Thread {
    private static final Logger LOG = LoggerFactory.getLogger(GPSPServiceThread.class);

    private final UserManager userManager;

    private Socket socket;

    public GPSPServiceThread(UserManager userManager, Socket socket) {
        super("GPSPServiceThread");
        this.userManager = userManager;
        this.socket = socket;
    }

    // SEE GP/gpiSearch.c

    // requests:
    //      search - search profile
    //      searchunique - search profile uniquenick
    //      valid - check if valid email address?
    //      nicks - search nicks
    //      pmatch - search players (other players playing this game?)
    //      check - search check
    //      newuser - new user?
    //      others - search other's buddy
    //      otherslist - search others buddy list
    //      uniquesearch - search suggest unique (for suggesting a unique nickname, given a preferred nickname)

    // responses:
    //      search
    //      searchunique
    //          bsr / bsrdone / more (multiple)
    //      valid
    //          vr
    //      nicks
    //          nr / ndone (multiple)
    //      pmatch
    //          psr / psrdone  (multiple)
    //      check
    //          cur
    //      newuser
    //          nur
    //      others
    //          o / odone (multiple)
    //      otherslist
    //          o / oldone (multiple)
    //      uniquesearch
    //          us / usdone
    //
    // * start of record's value is the profile id (eg. bsr)
    // * end of list has no value (eg. bsrdone)

    public void run() {

        try (
                InputStream reader = socket.getInputStream();
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {

            while (true) {
                String clientString = GPMessageUtils.readGPMessage(reader);
                if (clientString.isEmpty()) {
                    LOG.info("Client {} disconnected.", socket.getRemoteSocketAddress());
                    break;
                }

                String directive = GPMessageUtils.getGPDirective(clientString);
                LOG.info("profile request: {}", clientString);

                if(directive == null) {
                    // unrecognized message format
                    LOG.warn("Unrecognized message format. Message: " + clientString);
                } else if(directive.equals("searchunique")) {
                    // this is querying everyone on your PSN friends list one by one, by their PSN username
                    // this is necessary in order to populate the friends list when you want to send game invites
                    Map<String, String> inputMap = GPMessageUtils.parseClientLogin(clientString);

                    // \searchunique\\sesskey\5555\profileid\7777\ uniquenick\CSlucher818\namespaces\28\gamename\bstormps3\final\

                    final String name = inputMap.get("uniquenick");
                    var profileID = UserUtils.profileID(name);

                    this.userManager.registerUser(profileID, name);

                    // searchunique result data
                    Map<String,String> responseDataMap = new LinkedHashMap<>();
                    responseDataMap.put("bsr", Long.toString(profileID));
                    responseDataMap.put("nick", name);
                    responseDataMap.put("uniquenick", name);
                    responseDataMap.put("namespaceid", inputMap.get("namespaces"));
                    responseDataMap.put("bsrdone", "");

                    var msg = GPMessageUtils.createGPMessage(responseDataMap);
                    LOG.info("reply: {}", msg);
                    socket.getOutputStream().write(msg.getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().flush();
                } else {
                    LOG.info("UNKNOWN DIRECTIVE: {}", directive);
                    // directive unknown/not implemented yet
//                        String clientRequestString = GPMessageUtils.readGPMessage(reader);
//                        if(clientRequestString != null && clientRequestString.length() > 0) {
//                            LOG.info("UNKNOWN DIRECTIVE: " + clientRequestString);
//                        }
                }

            }

        } catch (IOException e) {
            LOG.info("Client closed exception");
            //e.printStackTrace();
        } finally {
            try {
//                LOG.info("Closing socket");
                socket.close();
            } catch(IOException e) {
                LOG.info("Could not close socket. Oh well..");
            }
        }
    }

}
