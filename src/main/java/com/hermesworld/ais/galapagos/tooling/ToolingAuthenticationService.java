package com.hermesworld.ais.galapagos.tooling;

import javax.annotation.CheckReturnValue;
import java.io.OutputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public interface ToolingAuthenticationService {

    Optional<ToolingAuthenticationMetadata> getToolingAuthentication(String environmentId);

    @CheckReturnValue
    CompletableFuture<ToolingAuthenticationMetadata> createToolingAuthentication(String environmentId,
            OutputStream outputStreamForSecret, String applicationName) throws ExecutionException, InterruptedException;

    @CheckReturnValue
    CompletableFuture<Void> deleteToolingAuthentication(String environmentId);

}
