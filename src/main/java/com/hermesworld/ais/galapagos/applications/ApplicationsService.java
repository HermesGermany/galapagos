package com.hermesworld.ais.galapagos.applications;

import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public interface ApplicationsService {

    List<? extends KnownApplication> getKnownApplications(boolean excludeUserApps);

    Optional<KnownApplication> getKnownApplication(String applicationId);

    Optional<ApplicationMetadata> getApplicationMetadata(String environmentId, String applicationId);

    CompletableFuture<ApplicationOwnerRequest> submitApplicationOwnerRequest(String applicationId, String comments);

    List<ApplicationOwnerRequest> getUserApplicationOwnerRequests();

    List<ApplicationOwnerRequest> getAllApplicationOwnerRequests();

    CompletableFuture<ApplicationOwnerRequest> updateApplicationOwnerRequest(String requestId, RequestState newState);

    CompletableFuture<Boolean> cancelUserApplicationOwnerRequest(String requestId);

    List<? extends KnownApplication> getUserApplications();

    default List<ApplicationMetadata> getAllApplicationMetadata(String environmentId) {
        return getAllApplicationOwnerRequests().stream().map(req -> req.getApplicationId()).distinct()
                .map(id -> getApplicationMetadata(environmentId, id).orElse(null)).filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    boolean isUserAuthorizedFor(String applicationId);

    CompletableFuture<ApplicationMetadata> createApplicationCertificateFromCsr(String environmentId,
            String applicationId, String csrData, String topicPrefix, boolean extendCertificate,
            OutputStream outputStreamForCerFile);

    CompletableFuture<ApplicationMetadata> createApplicationCertificateAndPrivateKey(String environmentId,
            String applicationId, String topicPrefix, OutputStream outputStreamForP12File);

}
