package com.hermesworld.ais.galapagos.applications.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.type.TypeFactory;
import com.hermesworld.ais.galapagos.applications.*;
import com.hermesworld.ais.galapagos.changes.Change;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.staging.Staging;
import com.hermesworld.ais.galapagos.staging.StagingResult;
import com.hermesworld.ais.galapagos.staging.StagingService;
import com.hermesworld.ais.galapagos.util.CertificateUtil;
import com.hermesworld.ais.galapagos.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Slf4j
public class ApplicationsController {

    private ApplicationsService applicationsService;

    private StagingService stagingService;

    private KafkaClusters kafkaClusters;

    @Autowired
    public ApplicationsController(ApplicationsService applicationsService, StagingService stagingService,
            KafkaClusters kafkaClusters) {
        this.applicationsService = applicationsService;
        this.stagingService = stagingService;
        this.kafkaClusters = kafkaClusters;
    }

    @GetMapping(value = "/api/applications", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<KnownApplicationDto> listApplications(
            @RequestParam(name = "excludeUserApps", required = false, defaultValue = "false") boolean excludeUserApps) {
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
            if (!b.booleanValue()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e, "Could not retrieve user's requests: ");
        }
        catch (InterruptedException e) {
            return;
        }
    }

    @GetMapping(value = "/api/certificates/{applicationId}")
    public List<ApplicationCertificateDto> getApplicationCertificates(@PathVariable String applicationId) {
        return kafkaClusters.getEnvironmentsMetadata().stream()
                .map(env -> applicationsService.getApplicationMetadata(env.getId(), applicationId)
                        .map(meta -> toAppCertDto(env.getId(), meta)).orElse(null))
                .filter(dto -> dto != null).collect(Collectors.toList());
    }

    @PostMapping(value = "/api/certificates/{applicationId}/{environmentId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CertificateResponseDto updateApplicationCertificate(@PathVariable String applicationId,
            @PathVariable String environmentId, @RequestBody CertificateRequestDto request) {
        applicationsService.getKnownApplication(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!applicationsService.isUserAuthorizedFor(applicationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        kafkaClusters.getEnvironmentMetadata(environmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        String topicPrefix = request.getTopicPrefix();
        if (StringUtils.isEmpty(topicPrefix)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Please include a prefix for app internal topics (field topicPrefix)");
        }

        if (!request.isGenerateKey()) {
            String csrData = request.getCsrData();
            if (StringUtils.isEmpty(csrData)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No CSR (csrData) present! Set generateKey to true if you want the server to generate a private key for you (not recommended).");
            }

            return createCertificate(environmentId, applicationId, csrData, topicPrefix, request.isExtendCertificate());
        }
        else {
            if (environmentId.equals(kafkaClusters.getProductionEnvironmentId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "You cannot get a server-side generated key for production environments.");
            }
            return createCertificateAndKey(environmentId, applicationId, topicPrefix);
        }
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
        if (!StringUtils.isEmpty(stagingFilterRaw)) {
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

    private CertificateResponseDto createCertificate(String environmentId, String applicationId, String csrData,
            String topicPrefix, boolean extendCertificate) {
        try {
            ByteArrayOutputStream cerOut = new ByteArrayOutputStream();
            ApplicationMetadata metadata = applicationsService.createApplicationCertificateFromCsr(environmentId,
                    applicationId, csrData, topicPrefix, extendCertificate, cerOut).get();

            CertificateResponseDto dto = new CertificateResponseDto();
            dto.setFileName(CertificateUtil.extractCn(metadata.getDn()) + "_" + environmentId + ".cer");
            dto.setFileContentsBase64(Base64.getEncoder().encodeToString(cerOut.toByteArray()));
            return dto;
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e, "Could not generate a certificate from your CSR request: ");
        }
        catch (InterruptedException e) {
            return null;
        }
    }

    private CertificateResponseDto createCertificateAndKey(String environmentId, String applicationId,
            String topicPrefix) {
        try {
            ByteArrayOutputStream outP12 = new ByteArrayOutputStream();
            ApplicationMetadata metadata = applicationsService
                    .createApplicationCertificateAndPrivateKey(environmentId, applicationId, topicPrefix, outP12).get();

            CertificateResponseDto dto = new CertificateResponseDto();
            String cn = CertificateUtil.extractCn(metadata.getDn());
            dto.setFileName(cn + "_" + environmentId + ".p12");
            dto.setFileContentsBase64(Base64.getEncoder().encodeToString(outP12.toByteArray()));
            return dto;
        }
        catch (ExecutionException e) {
            throw handleExecutionException(e, "Could not generate a signed certificate and private key: ");
        }
        catch (InterruptedException e) {
            return null;
        }
    }

    private ApplicationCertificateDto toAppCertDto(String environmentId, ApplicationMetadata metadata) {
        return new ApplicationCertificateDto(environmentId, metadata.getDn(),
                metadata.getCertificateExpiresAt().toInstant().toString());
    }

    private ResponseStatusException handleExecutionException(ExecutionException e, String msgPrefix) {
        Throwable t = e.getCause();
        if (t instanceof CertificateException) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, msgPrefix + t.getMessage());
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
        return new KnownApplicationDto(app.getId(), app.getName(),
                app.getInfoUrl() == null ? null : app.getInfoUrl().toString(), toCapDtos(app.getBusinessCapabilities()),
                new ArrayList<>(app.getAliases()));
    }

    private List<BusinessCapabilityDto> toCapDtos(List<? extends BusinessCapability> caps) {
        return caps.stream().map(cap -> new BusinessCapabilityDto(cap.getId(), cap.getName()))
                .collect(Collectors.toList());
    }

}
