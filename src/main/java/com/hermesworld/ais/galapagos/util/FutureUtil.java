package com.hermesworld.ais.galapagos.util;

import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;

public final class FutureUtil {

    private static final CompletableFuture<Void> noop = CompletableFuture.completedFuture(null);

    private FutureUtil() {
    }

    public static <T> CompletableFuture<T> noUser() {
        return CompletableFuture
                .failedFuture(new IllegalStateException("A user must be logged in for this operation."));
    }

    public static <T> CompletableFuture<T> noSuchEnvironment(String environmentId) {
        return CompletableFuture
                .failedFuture(new NoSuchElementException("No environment with ID " + environmentId + " found."));
    }

    public static CompletableFuture<Void> noop() {
        return noop;
    }

}
