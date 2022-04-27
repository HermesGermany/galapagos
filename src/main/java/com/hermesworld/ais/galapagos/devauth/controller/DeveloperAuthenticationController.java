package com.hermesworld.ais.galapagos.devauth.controller;

import com.hermesworld.ais.galapagos.applications.controller.AuthenticationDto;
import com.hermesworld.ais.galapagos.ccloud.apiclient.ConfluentApiException;
import com.hermesworld.ais.galapagos.ccloud.auth.ConfluentCloudAuthUtil;
import com.hermesworld.ais.galapagos.devauth.DevAuthenticationMetadata;
import com.hermesworld.ais.galapagos.devauth.DeveloperAuthenticationService;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;

@RestController
@Slf4j
public class DeveloperAuthenticationController {

    private final DeveloperAuthenticationService authService;

    private final CurrentUserService userService;

    private final KafkaClusters kafkaClusters;

    public DeveloperAuthenticationController(DeveloperAuthenticationService authService, CurrentUserService userService,
            KafkaClusters kafkaClusters) {
        this.authService = authService;
        this.userService = userService;
        this.kafkaClusters = kafkaClusters;
    }

    @PostMapping(value = "/api/me/certificates/{environmentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DeveloperCertificateDto createDeveloperCertificate(@PathVariable String environmentId) {
        String userName = userService.getCurrentUserName()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            authService.createDeveloperAuthenticationForCurrentUser(environmentId, baos).get();
            return new DeveloperCertificateDto(userName + "_" + environmentId + ".p12",
                    Base64.getEncoder().encodeToString(baos.toByteArray()));
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @PostMapping(value = "/api/me/apikey/{environmentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DeveloperApiKeyDto createDeveloperApiKey(@PathVariable String environmentId) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            DevAuthenticationMetadata metadata = authService
                    .createDeveloperAuthenticationForCurrentUser(environmentId, baos).get();
            return new DeveloperApiKeyDto(ConfluentCloudAuthUtil.getApiKey(metadata.getAuthenticationJson()),
                    baos.toString(StandardCharsets.UTF_8),
                    ConfluentCloudAuthUtil.getExpiresAt(metadata.getAuthenticationJson()));
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @GetMapping(value = "/api/me/authentications/{environmentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DeveloperAuthenticationsDto getDeveloperAuthenticationInfo(@PathVariable String environmentId) {
        Map<String, AuthenticationDto> devAuthPerEnv = new HashMap<>();
        for (KafkaEnvironmentConfig env : kafkaClusters.getEnvironmentsMetadata()) {
            DevAuthenticationMetadata metadata = authService.getDeveloperAuthenticationOfCurrentUser(environmentId)
                    .orElse(null);
            if (metadata != null && metadata.getAuthenticationJson() != null) {
                AuthenticationDto dto = new AuthenticationDto();
                dto.setAuthenticationType(env.getAuthenticationMode());
                dto.setAuthentication(new JSONObject(metadata.getAuthenticationJson()).toMap());
                devAuthPerEnv.put(env.getId(), dto);
            }
        }

        DeveloperAuthenticationsDto result = new DeveloperAuthenticationsDto();
        result.setAuthentications(devAuthPerEnv);
        return result;
    }

    private ResponseStatusException handleExecutionException(ExecutionException e) {
        Throwable t = e.getCause();
        if (t instanceof CertificateException) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, t.getMessage());
        }
        if (t instanceof ConfluentApiException) {
            return new ResponseStatusException(HttpStatus.BAD_GATEWAY, t.getMessage());
        }
        if (t instanceof NoSuchElementException) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if ((t instanceof IllegalStateException) || (t instanceof IllegalArgumentException)) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, t.getMessage());
        }

        log.error("Unhandled exception in DeveloperAuthenticationController", t);
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
