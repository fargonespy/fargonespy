package com.gonespy.service;

import com.gonespy.service.auth.AuthService;
import com.gonespy.service.availability.AvailabilityService;
import com.gonespy.service.gpcm.GPCMService;
import com.gonespy.service.gpsp.GPSPService;
import com.gonespy.service.natneg.NatnegService;
import com.gonespy.service.sake.SakeService;
import com.gonespy.service.serverlist.ServerListService;
import com.gonespy.service.serverlist.ServerManager;
import com.gonespy.service.stats.GStatsService;
import com.gonespy.service.user.UserManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunAllServices {

    private static final String DISPLAY_NAME = RunAllServices.class.getSimpleName();
    private static final Logger LOG = LoggerFactory.getLogger(DISPLAY_NAME);

    public static void main(String[] args) {

        var sm = new ServerManager();
        var um = new UserManager();

        Thread availabilityServiceThread = new Thread(new AvailabilityService(sm));

        Thread gpcmServiceThread = new Thread(new GPCMService(um));

        Thread gpspServiceThread = new Thread(new GPSPService(um));

        Thread gstatsServiceThread = new Thread(() -> new GStatsService().run());

        Thread authServiceThread = new Thread(() -> {
            try {
                new AuthService(um).run("server", "resources/dw-auth-config.yml");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Thread sakeServiceThread = new Thread(() -> SakeService.main(new String[]{"server", "resources/dw-sake-config.yml"}));

        Thread serverListServiceThread = new Thread(new ServerListService(sm));

        Thread natnegServiceThread = new Thread(new NatnegService());

        gpcmServiceThread.start();
        availabilityServiceThread.start();
        gpspServiceThread.start();
        gstatsServiceThread.start();

        authServiceThread.start();
        DropwizardProbe.probeOnPort(443, true);

        sakeServiceThread.start();
        DropwizardProbe.probeOnPort(80, true);

        serverListServiceThread.start();
        natnegServiceThread.start();

        LOG.info("=== ALL SERVICES STARTED! ===");

    }



}
