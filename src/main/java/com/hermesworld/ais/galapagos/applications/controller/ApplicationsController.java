package com.hermesworld.ais.galapagos.applications.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.hermesworld.ais.galapagos.applications.*;
import com.hermesworld.ais.galapagos.ccloud.apiclient.ConfluentApiException;
import com.hermesworld.ais.galapagos.ccloud.auth.ConfluentCloudAuthUtil;
import com.hermesworld.ais.galapagos.changes.Change;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.naming.ApplicationPrefixes;
import com.hermesworld.ais.galapagos.staging.Staging;
import com.hermesworld.ais.galapagos.staging.StagingResult;
import com.hermesworld.ais.galapagos.staging.StagingService;
import com.hermesworld.ais.galapagos.util.CertificateUtil;
import com.hermesworld.ais.galapagos.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@RestController
@Slf4j
public class ApplicationsController {

    private final ApplicationsService applicationsService;

    private final StagingService stagingService;

    private final KafkaClusters kafkaClusters;

    public ApplicationsController(ApplicationsService applicationsService, StagingService stagingService,
            KafkaClusters kafkaClusters) {
        this.applicationsService = applicationsService;
        this.stagingService = stagingService;
        this.kafkaClusters = kafkaClusters;
    }

    @GetMapping(value = "/api/applications", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<KnownApplicationDto> listApplications(
            @RequestParam(required = false, defaultValue = "false") boolean excludeUserApps) {
        return applicationsService.getKnownApplications(excludeUserApps).stream().map(app -> toKnownAppDto(app))
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/api/me/applications", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<KnownApplicationDto> listUserApplications() {
        return applicationsService.getUserApplications().stream().map(app -> toKnownAppDto(app))
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/api/me/requests", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ApplicationOwnerRequest> getUserApplicationOwnerRequests() {
        return applicationsService.getUserApplicationOwnerRequests();
    }

    @PutMapping(value = "/api/me/requests", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ApplicationOwnerRequest submitApplicationOwnerRequest(
            @RequestBody ApplicationOwnerRequestSubmissionDto request) {
        if (StringUtils.isEmpty(request.getApplicationId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Required parameter applicationId is missing or empty");
        }
        applicationsService.getKnownApplication(request.getApplicationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        try {
            return applicationsService.submitApplicationOwnerRequest(request.getApplicationId(), request.getComments())
                    .get();
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e, "Could not submit application owner request: ");
        }
        catch (InterruptedException e) {
            return null;
        }
    }

    @GetMapping(value = "/api/registered-applications/{envId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<KnownApplicationDto> getRegisteredApplications(@PathVariable String envId) {
        return applicationsService.getAllApplicationMetadata(envId).stream()
                .map(app -> toKnownAppDto(applicationsService.getKnownApplication(app.getApplicationId()).orElse(null)))
                .filter(app -> app != null).collect(Collectors.toList());
    }

    @GetMapping(value = "/api/admin/requests", produces = MediaType.APPLICATION_JSON_VALUE)
    @Secured("ROLE_ADMIN")
    public List<ApplicationOwnerRequest> getAllApplicationOwnerRequests() {
        return applicationsService.getAllApplicationOwnerRequests();
    }

    @PostMapping(value = "/api/admin/requests/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Secured("ROLE_ADMIN")
    public ApplicationOwnerRequest updateApplicationOwnerRequest(@PathVariable String id,
            @RequestBody UpdateApplicationOwnerRequestDto updateData) {
        try {
            return applicationsService.updateApplicationOwnerRequest(id, updateData.getNewState()).get();
        }
        catch (InterruptedException e) {
            return null;
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e, "Could not update application owner request: ");
        }
    }

    @DeleteMapping(value = "/api/me/requests/{id}")
    public void cancelApplicationOwnerRequest(@PathVariable String id) {
        try {
            Boolean b = applicationsService.cancelUserApplicationOwnerRequest(id).get();
            if (!b) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e, "Could not retrieve user's requests: ");
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @GetMapping(value = "/api/environments/{environmentId}/prefixes/{applicationId}")
    public ApplicationPrefixesDto getApplicationPrefixes(@PathVariable String environmentId,
            @PathVariable String applicationId) {
        return applicationsService.getApplicationMetadata(environmentId, applicationId)
                .map(metadata -> toPrefixesDto(metadata))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping(value = "/api/certificates/{applicationId}/{environmentId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CertificateResponseDto updateApplicationCertificate(@PathVariable String applicationId,
            @PathVariable String environmentId, @RequestBody CertificateRequestDto request) {
        KnownApplication app = applicationsService.getKnownApplication(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!applicationsService.isUserAuthorizedFor(applicationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        kafkaClusters.getEnvironmentMetadata(environmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        String filename = CertificateUtil.toAppCn(app.getName()) + "_" + environmentId;
        if (!request.isGenerateKey()) {
            String csrData = request.getCsrData();
            if (ObjectUtils.isEmpty(csrData)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No CSR (csrData) present! Set generateKey to true if you want the server to generate a private key for you (not recommended).");
            }
            filename += ".cer";
        }
        else {
            if (environmentId.equals(kafkaClusters.getProductionEnvironmentId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "You cannot get a server-side generated key for production environments.");
            }
            filename += ".p12";
        }

        try {
            JSONObject requestParams = new JSONObject(JsonUtil.newObjectMapper().writeValueAsString(request));
            ByteArrayOutputStream cerOut = new ByteArrayOutputStream();

            applicationsService.registerApplicationOnEnvironment(environmentId, applicationId, requestParams, cerOut)
                    .get();

            CertificateResponseDto dto = new CertificateResponseDto();
            dto.setFileName(filename);
            dto.setFileContentsBase64(Base64.getEncoder().encodeToString(cerOut.toByteArray()));
            return dto;
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e, "Could not create certificate: ");
        }
        catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON body");
        }
        catch (InterruptedException e) {
            return null;
        }
    }

    @PostMapping(value = "/api/apikeys/{applicationId}/{environmentId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CreatedApiKeyDto createApiKeyForApplication(@PathVariable String environmentId,
            @PathVariable String applicationId, @RequestBody String request) {
        applicationsService.getKnownApplication(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!applicationsService.isUserAuthorizedFor(applicationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        kafkaClusters.getEnvironmentMetadata(environmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            JSONObject params = ObjectUtils.isEmpty(request) ? new JSONObject() : new JSONObject(request);
            ApplicationMetadata metadata = applicationsService
                    .registerApplicationOnEnvironment(environmentId, applicationId, params, baos).get();
            CreatedApiKeyDto dto = new CreatedApiKeyDto();
            dto.setApiKey(ConfluentCloudAuthUtil.getApiKey(metadata.getAuthenticationJson()));
            dto.setApiSecret(baos.toString(StandardCharsets.UTF_8));
            return dto;
        }
        catch (JSONException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e, "Could not create API Key: ");
        }
        catch (InterruptedException e) {
            return null;
        }
    }

    @GetMapping(value = "/api/authentications/{applicationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApplicationAuthenticationsDto listApplicationAuthentications(@PathVariable String applicationId) {
        if (!applicationsService.isUserAuthorizedFor(applicationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        Map<String, AuthenticationDto> authPerEnv = new HashMap<>();
        for (KafkaEnvironmentConfig env : kafkaClusters.getEnvironmentsMetadata()) {
            ApplicationMetadata metadata = applicationsService.getApplicationMetadata(env.getId(), applicationId)
                    .orElse(null);
            if (metadata != null && metadata.getAuthenticationJson() != null) {
                AuthenticationDto dto = new AuthenticationDto();
                dto.setAuthenticationType(env.getAuthenticationMode());
                dto.setAuthentication(new JSONObject(metadata.getAuthenticationJson()).toMap());
                authPerEnv.put(env.getId(), dto);
            }
        }

        ApplicationAuthenticationsDto result = new ApplicationAuthenticationsDto();
        result.setAuthentications(authPerEnv);
        return result;
    }

    @GetMapping(value = "/api/environments/{environmentId}/staging/{applicationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Staging describeStaging(@PathVariable String environmentId, @PathVariable String applicationId) {
        if (!applicationsService.isUserAuthorizedFor(applicationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        try {
            return stagingService.prepareStaging(applicationId, environmentId, Collections.emptyList()).get();
        }
        catch (ExecutionException e) {
            // noinspection ThrowableNotThrown
            handleExecutionException(e, "Could not prepare staging: ");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        catch (InterruptedException e) {
            return null;
        }
    }

    @PostMapping(value = "/api/environments/{environmentId}/staging/{applicationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<StagingResult> performStaging(@PathVariable String environmentId, @PathVariable String applicationId,
            @RequestBody String stagingFilterRaw) {
        if (!applicationsService.isUserAuthorizedFor(applicationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        List<Change> stagingFilter = null;
        if (!ObjectUtils.isEmpty(stagingFilterRaw)) {
            try {
                stagingFilter = JsonUtil.newObjectMapper().readValue(stagingFilterRaw,
                        TypeFactory.defaultInstance().constructCollectionType(ArrayList.class, Change.class));
            }
            catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
        }

        try {
            return stagingService.prepareStaging(applicationId, environmentId, stagingFilter).get().perform();
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e, "Could not prepare staging: ");
        }
        catch (InterruptedException e) {
            return Collections.emptyList();
        }
    }

    private ResponseStatusException handleExecutionException(ExecutionException e, String msgPrefix) {
        Throwable t = e.getCause();
        if (t instanceof CertificateException) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, msgPrefix + t.getMessage());
        }
        if (t instanceof ConfluentApiException) {
            log.error("Encountered Confluent API Exception", t);
            return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        if (t instanceof NoSuchElementException) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if ((t instanceof IllegalStateException) || (t instanceof IllegalArgumentException)) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, t.getMessage());
        }

        log.error("Unhandled exception in ApplicationsController", t);
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private KnownApplicationDto toKnownAppDto(KnownApplication app) {
        if (app == null) {
            return null;
        }
        return new KnownApplicationDto(app.getId(), app.getName(),
                app.getInfoUrl() == null ? null : app.getInfoUrl().toString(), toCapDtos(app.getBusinessCapabilities()),
                new ArrayList<>(app.getAliases()));
    }

    private List<BusinessCapabilityDto> toCapDtos(List<? extends BusinessCapability> caps) {
        return caps.stream().map(cap -> new BusinessCapabilityDto(cap.getId(), cap.getName()))
                .collect(Collectors.toList());
    }

    private ApplicationPrefixesDto toPrefixesDto(ApplicationPrefixes prefixes) {
        return new ApplicationPrefixesDto(prefixes.getInternalTopicPrefixes(), prefixes.getConsumerGroupPrefixes(),
                prefixes.getTransactionIdPrefixes());
    }

}
