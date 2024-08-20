package com.gonespy.service.auth.resources;

import static com.gonespy.service.shared.Constants.DUMMY_PARTNER_CHALLENGE;
import static com.gonespy.service.shared.Constants.PEER_KEY_PRIVATE;

import com.gonespy.service.user.UserManager;
import com.gonespy.service.util.CertificateUtils;
import com.gonespy.service.util.SoapUtils;
import com.gonespy.service.util.StringUtils;
import io.swagger.annotations.Api;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// AuthService = HTTPS : 443

@Api(value = "gonespy")
@Path("/")
@Produces(MediaType.TEXT_XML)
public class AuthResource {
  private static final Logger LOG = LoggerFactory.getLogger(AuthResource.class);

  private static final String LOGIN_PS3_CERT_RESULT = "LoginPs3CertResult";
  private static final String LOGIN_REMOTE_AUTH_RESULT = "LoginRemoteAuthResult";
  private final UserManager userManager;

  public AuthResource(UserManager userManager) {
    this.userManager = userManager;
  }

  @POST
  @Consumes(MediaType.TEXT_XML)
  @Path("AuthService/AuthService.asmx")
  public Response authService(String request /*HttpConnection debugInformation*/) {
    return generateResponse(request);
  }

  // just for testing without SSH/POST getting in the way
  /*@GET
  @Path("/AuthService/GetAuthService.asmx")
  public Response getAuthService(String request) {
      return generateResponse(request);
  }*/

  private Response generateResponse(String request) {
    LOG.info("REQUEST:");
    LOG.info(request);

    Map<String, Object> map = SoapUtils.parseSoapData(request);
    String requestType = (String) map.get("ns1");
    Response response = null;
    switch (requestType) {
      case "LoginPs3Cert":
        response = loginPs3AuthResponse(map);
        break;
      case "LoginRemoteAuth":
        response = loginRemoteAuthResponse(map);
    }
    return response;
  }

  private Response loginPs3AuthResponse(Map<String, Object> requestData) {
    var nt = requestData.get("npticket");
    if (nt == null) {
      LOG.error("request missing npticket");
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    var tv = (String) ((Map<String, Object>) nt).get("Value");
    if (tv == null) {
      LOG.error("request missing npticket value");
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    var dtv = Base64.getDecoder().decode(tv);
    if (dtv.length < 100) {
      LOG.error("request npticket value too short");
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    var nb = new StringBuilder();
    for (int i = 84; i < dtv.length; i++) {
      if (dtv[i] == 0) {
        break;
      }
      nb.append((char) dtv[i]);
    }

    var user = nb.toString();
    var authToken = StringUtils.randomString(32);
    LOG.info("username {} auth token {}", user, authToken);
    this.userManager.trackAuthToken(authToken, user);

    // offset 84

    Map<String, Object> soapData = new LinkedHashMap<>();
    soapData.put("responseCode", "0");
    //        soapData.put("authToken", DUMMY_AUTH_TOKEN);
    soapData.put("authToken", authToken);
    soapData.put("partnerChallenge", DUMMY_PARTNER_CHALLENGE);

    String response = generateSoapResponse(LOGIN_PS3_CERT_RESULT, soapData);
    LOG.info("RESPONSE:");
    LOG.info(response);
    return Response.status(Response.Status.OK).entity(response).build();
  }

  private Response loginRemoteAuthResponse(Map<String, Object> inputData) {
    LOG.warn("WARNING: game uses remote auth login which does not work!");

    Map<String, Object> soapData = new LinkedHashMap<>();
    soapData.put("responseCode", "0");
    soapData.put(
        "certificate",
        // CertificateUtils.getCertificate(inputData)
        CertificateUtils.getCertificateEaEmu(inputData));
    soapData.put("peerkeyprivate", PEER_KEY_PRIVATE);

    String response = generateSoapResponse(LOGIN_REMOTE_AUTH_RESULT, soapData);
    LOG.info("RESPONSE:");
    LOG.info(response);
    return Response.status(Response.Status.OK).entity(response).build();
  }

  private String generateSoapResponse(String type, Map<String, Object> soapData) {
    StringBuilder b = new StringBuilder();
    for (String key : soapData.keySet()) {
      b.append(SoapUtils.createSoapTagWithValue(key, soapData.get(key)));
    }
    return SoapUtils.wrapSoapData("AuthService/", type, b.toString());
  }
}
