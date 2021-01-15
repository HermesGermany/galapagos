package com.hermesworld.ais.galapagos.changes;

import java.util.concurrent.CompletableFuture;

public interface ApplicableChange extends Change {

    CompletableFuture<?> applyTo(ApplyChangeContext context);

}
