package com.hermesworld.ais.galapagos.applications;

import org.json.JSONObject;

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

    CompletableFuture<ApplicationMetadata> registerApplicationOnEnvironment(String environmentId, String applicationId,
            JSONObject registerParams, OutputStream outputStreamForSecret);

    /**
     * Resets the allowed prefixes for the given application on the given environment to their defaults, as resulting
     * from current Galapagos configuration for naming rules. Kafka ACLs are <b>not</b> updated by this method, and
     * <b>no</b> events are fired via the <code>EventManager</code>. <br>
     * This removes prefixes which have previously been assigned to the given application (e.g. due to different naming
     * rules or changed application aliases), so this could <b>break</b> running applications when using previously
     * assigned, but no longer available prefixes - once the resulting ACL changes are applied (not done by this
     * method). <br>
     * Currently, this functionality is only available using the admin job "reset-application-prefixes", which indeed
     * <b>does</b> also refresh the associated Kafka ACLs.
     *
     * @param environmentId ID of Kafka cluster to operate on.
     * @param applicationId ID of application to reset prefixes of.
     *
     * @return A CompletableFuture completing once Application Metadata has been updated, or failing when any ID is
     *         invalid, or any other error occurs.
     */
    CompletableFuture<ApplicationMetadata> resetApplicationPrefixes(String environmentId, String applicationId);

}
