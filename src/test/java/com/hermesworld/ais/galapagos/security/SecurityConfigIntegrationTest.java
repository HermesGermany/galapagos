package com.hermesworld.ais.galapagos.security;

import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.applications.controller.ApplicationsController;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.security.config.GalapagosSecurityProperties;
import com.hermesworld.ais.galapagos.staging.StagingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = { SecurityConfig.class, ApplicationsController.class,
        GalapagosSecurityProperties.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableAutoConfiguration(exclude = OAuth2ClientAutoConfiguration.class)
class SecurityConfigIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @SuppressWarnings("unused")
    @MockBean
    private ApplicationsService applicationsService;

    @SuppressWarnings("unused")
    @MockBean
    private StagingService stagingService;

    @SuppressWarnings("unused")
    @MockBean
    private KafkaClusters kafkaClusters;

    @MockBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void initJwtStuff() {
        when(jwtDecoder.decode(any())).thenAnswer(inv -> {
            String token = inv.getArgument(0);
            Map<String, Object> headers = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> claims = Map.of("sub", "abc123", "iat", "123", "my_roles", token.replace(".", " "));
            return new Jwt(token, Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS), headers, claims);
        });
    }

    @Test
    void test_apiAccessProtected() {
        ResponseEntity<String> response = restTemplate.getForEntity("http://localhost:" + port + "/api/me/requests",
                String.class);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
    }

    @Test
    void test_apiAccess_missingUserRole() {
        testApiWithRole("/api/me/requests", "NOT_A_USER", HttpStatus.FORBIDDEN.value());
    }

    @Test
    void test_apiAccess_withUserRole() {
        testApiWithRole("/api/me/requests", "USER", HttpStatus.OK.value());
    }

    @Test
    void test_apiAccess_adminEndpoint_withUserRole() {
        testApiWithRole("/api/admin/requests", "USER", HttpStatus.FORBIDDEN.value());
    }

    @Test
    void test_apiAccess_adminEndpoint_withAdminRole() {
        testApiWithRole("/api/admin/requests", "USER.ADMIN", HttpStatus.OK.value());
    }

    private void testApiWithRole(String endpoint, String roleName, int expectedCode) {
        String url = "http://localhost:" + port + endpoint;

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + roleName);

        HttpEntity<String> request = new RequestEntity<>(headers, HttpMethod.GET, URI.create(url));
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
        assertEquals(expectedCode, response.getStatusCode().value());
    }

}
