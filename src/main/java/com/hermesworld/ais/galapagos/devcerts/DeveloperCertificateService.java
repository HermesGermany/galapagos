package com.hermesworld.ais.galapagos.devcerts;

import javax.annotation.CheckReturnValue;
import java.io.OutputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface DeveloperCertificateService {

    Optional<DevCertificateMetadata> getDeveloperCertificateOfCurrentUser(String environmentId);

    @CheckReturnValue
    CompletableFuture<Void> createDeveloperCertificateForCurrentUser(String environmentId,
            OutputStream p12OutputStream);

    @CheckReturnValue
    CompletableFuture<Integer> clearExpiredDeveloperCertificatesOnAllClusters();

}
