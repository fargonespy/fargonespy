package com.gonespy.service.sake.resources;

import io.swagger.annotations.Api;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api
@Path("/")
@Produces(MediaType.TEXT_HTML)
public class InstructionResource {

  @GET
  @Path("/")
  public String getInstructions() {
    String address = null;
    try {
      for (var intf : Collections.list(NetworkInterface.getNetworkInterfaces())) {
        System.out.println("interface " + intf);
        for (var addr : Collections.list(intf.getInetAddresses())) {
          if (addr.isLoopbackAddress() || !(addr instanceof Inet4Address)) {
            continue;
          }
          address = addr.getHostAddress();
        }
      }
    } catch (SocketException e) {
      throw new RuntimeException(e);
    }

    var sb = new StringBuilder();
    sb.append("<div style='font-size: 30px;'>");
    sb.append("FarGoneSpy is up and running.<br/><br/>");
    if (address != null) {
      sb.append("Configure your PS3 settings to use custom DNS server at ")
          .append(address)
          .append(" which should be the address of this computer.");
    }
    sb.append("</div>");
    return sb.toString();
  }
}
