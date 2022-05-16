package com.hermesworld.ais.galapagos.devauth;

import javax.annotation.CheckReturnValue;
import java.io.OutputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface DeveloperAuthenticationService {

    Optional<DevAuthenticationMetadata> getDeveloperAuthenticationOfCurrentUser(String environmentId);

    @CheckReturnValue
    CompletableFuture<DevAuthenticationMetadata> createDeveloperAuthenticationForCurrentUser(String environmentId,
            OutputStream outputStreamForSecret);

    @CheckReturnValue
    CompletableFuture<Integer> clearExpiredDeveloperAuthenticationsOnAllClusters();

}
