package com.hermesworld.ais.galapagos.devcerts;

import java.io.OutputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface DeveloperCertificateService {

    Optional<DevCertificateMetadata> getDeveloperCertificateOfCurrentUser(String environmentId);

    CompletableFuture<Void> createDeveloperCertificateForCurrentUser(String environmentId,
            OutputStream p12OutputStream);

}
