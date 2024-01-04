package com.hermesworld.ais.galapagos.security.impl;

import com.hermesworld.ais.galapagos.security.SecurityConfig;
import com.hermesworld.ais.galapagos.security.config.GalapagosSecurityProperties;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = { OAuthConfigController.class, SecurityConfig.class,
        GalapagosSecurityProperties.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableAutoConfiguration(exclude = OAuth2ClientAutoConfiguration.class)
class OAuthConfigControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private OAuth2ClientProperties oauthProperties;

    @MockBean
    @SuppressWarnings("unused")
    private JwtDecoder jwtDecoder;

    private MockWebServer oauthServer;

    @BeforeEach
    void initOauthPropertiesAndServer() throws Exception {
        oauthServer = new MockWebServer();
        oauthServer.setDispatcher(new Dispatcher() {
            @NotNull
            @Override
            public MockResponse dispatch(@NotNull RecordedRequest recordedRequest) {
                String path = recordedRequest.getPath();
                if (path == null) {
                    return new MockResponse().setResponseCode(404);
                }
                if (recordedRequest.getPath().endsWith("/openid-configuration")) {
                    return new MockResponse().setBody(readOpenidConfig()).setResponseCode(200)
                            .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                }

                return new MockResponse().setResponseCode(404);
            }
        });
        oauthServer.start(0);

        Map<String, OAuth2ClientProperties.Registration> oauthMap = new HashMap<>();
        OAuth2ClientProperties.Registration reg = new OAuth2ClientProperties.Registration();
        reg.setClientId("test-webapp");
        reg.setProvider("keycloak");
        reg.setScope(Set.of("email", "openid", "profile"));
        oauthMap.put("keycloak", reg);

        Map<String, OAuth2ClientProperties.Provider> providerMap = new HashMap<>();
        OAuth2ClientProperties.Provider provider = new OAuth2ClientProperties.Provider();
        provider.setIssuerUri("http://localhost:" + oauthServer.getPort() + "/auth/realms/galapagos");
        providerMap.put("keycloak", provider);

        when(oauthProperties.getRegistration()).thenReturn(oauthMap);
        when(oauthProperties.getProvider()).thenReturn(providerMap);
    }

    @AfterEach
    void shutdownServer() throws IOException {
        oauthServer.shutdown();
    }

    @Test
    void test_getOauthConfig() {
        ResponseEntity<String> response = restTemplate.getForEntity("http://localhost:" + port + "/oauth2/config.json",
                String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());

        JSONObject config = new JSONObject(response.getBody());
        assertEquals("test_username", config.get("userNameClaim"));
        assertEquals("my_roles", config.get("rolesClaim"));
        assertEquals("display_name", config.get("displayNameClaim"));
        assertEquals("test-webapp", config.get("clientId"));
    }

    private String readOpenidConfig() {
        try (InputStream in = OAuthConfigControllerIntegrationTest.class.getClassLoader()
                .getResourceAsStream("openid-config.json")) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8).replace("http://keycloak/",
                    "http://localhost:" + oauthServer.getPort() + "/");
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}