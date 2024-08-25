package com.gonespy.service.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.gonespy.service.auth.resources.AuthResource;
import com.gonespy.service.auth.resources.VersionResource;
import com.gonespy.service.user.UserManager;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.Application;
import io.dropwizard.configuration.ResourceConfigurationSourceProvider;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Security;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.text.StrSubstitutor;

public class AuthService extends Application<AuthServiceConfiguration> {
  private static final String KEYSTORE_FILE = "myKeyStore.jks";

  static {
    Security.setProperty("jdk.tls.disabledAlgorithms", "");
    Security.setProperty("https.cipherSuites", "SSL_RSA_WITH_RC4_128_MD5");
    Security.setProperty("jdk.tls.legacyAlgorithms", "SSL_RSA_WITH_RC4_128_MD5");
  }

  private final UserManager userManager;

  public AuthService(UserManager userManager) {
    this.userManager = userManager;
  }

  @Override
  public void initialize(Bootstrap<AuthServiceConfiguration> bootstrap) {
    var is = this.getClass().getClassLoader().getResourceAsStream(KEYSTORE_FILE);
    if (is == null) {
      throw new RuntimeException("could not find keystore resource...");
    }

    try {
      var tempDir = Files.createTempDirectory("fargonespy");
      var tempFile = Paths.get(tempDir.toString(), KEYSTORE_FILE);
      System.out.println("writing keystore to temp file " + tempFile);

      try (var os = new FileOutputStream(tempFile.toFile())) {
        IOUtils.copy(is, os);
      }

      bootstrap.setConfigurationSourceProvider(new ResourceConfigurationSourceProvider());

      var sub = new StrSubstitutor(ImmutableMap.of("keyStorePath", tempFile.toString()));
      bootstrap.setConfigurationSourceProvider(
          new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(), sub));

    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    bootstrap.addBundle(
        new SwaggerBundle<AuthServiceConfiguration>() {
          @Override
          protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(
              AuthServiceConfiguration configuration) {
            return configuration.swaggerBundleConfiguration;
          }
        });
  }

  @Override
  public void run(AuthServiceConfiguration configuration, Environment environment) {

    AuthResource authResource = new AuthResource(this.userManager);

    // resources
    environment.jersey().register(new VersionResource());
    environment.jersey().register(authResource);
    environment.jersey().register(new JerseyObjectMapper());

    environment.jersey().register(new JsonProcessingExceptionMapper());

    environment.getObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
    environment.getObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    environment.getObjectMapper().registerModule(new Jdk8Module());

    // health checks
    environment.healthChecks().register("dummy2", new DummyHealthCheck());
    // MetricRegistry retrievedMetricRegistry = SharedMetricRegistries.getOrCreate("default");
    // environment.metrics().register("registry2", retrievedMetricRegistry);

  }

  @Provider
  public static class JerseyObjectMapper implements ContextResolver<ObjectMapper> {

    private ObjectMapper mapper;

    public JerseyObjectMapper() {
      mapper = new ObjectMapper();
      mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
    }

    @Override
    public ObjectMapper getContext(Class<?> type) {
      return mapper;
    }
  }

  public class JsonProcessingExceptionMapper implements ExceptionMapper<JsonProcessingException> {
    @Override
    public Response toResponse(JsonProcessingException exception) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
  }
}
