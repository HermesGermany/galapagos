package com.hermesworld.ais.galapagos.devauth;

import javax.annotation.CheckReturnValue;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface DeveloperAuthenticationService {

    Optional<DevAuthenticationMetadata> getDeveloperAuthenticationOfCurrentUser(String environmentId);

    @CheckReturnValue
    CompletableFuture<DevAuthenticationMetadata> createDeveloperAuthenticationForCurrentUser(String environmentId,
            OutputStream outputStreamForSecret);

    @CheckReturnValue
    CompletableFuture<Integer> clearExpiredDeveloperAuthenticationsOnAllClusters();

    /**
     * Returns all known (and currently valid) developer authentications for the given Kafka Cluster.
     *
     * @param environmentId ID of the environment (Kafka Cluster) to return all valid developer authentications for.
     * @return A (possibly empty) list of developer authentications.
     */
    List<DevAuthenticationMetadata> getAllDeveloperAuthentications(String environmentId);

}
