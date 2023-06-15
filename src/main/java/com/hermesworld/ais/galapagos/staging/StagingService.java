package com.hermesworld.ais.galapagos.staging;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.hermesworld.ais.galapagos.changes.Change;

/**
 * This service can be used to calculate a {@link Staging} object, which describes the required changes to be performed
 * on an environment to bring it up-to-date with the given source environment, for a given application.
 *
 * @author AlbrechtFlo
 *
 */
public interface StagingService {

    /**
     * "Prepares" the staging for a given application and a given source environment. This means,
     * <ul>
     * <li>determining the appropriate target environment,</li>
     * <li>calculating the differences between given source and the target environment, for the given application.</li>
     * </ul>
     * If the optional <code>changesFilter</code> is provided and not empty, the result is filtered, and only changes
     * equal to one of the changes in the given filter list are included. Note that this may lead to an inconsistent
     * change set, which may not fully be applied to the target environment successfully. <br>
     * <br>
     * To perform the staging, call <code>perform()</code> on the (asynchronously) returned <code>Staging</code> object.
     *
     * @param applicationId     ID of the application to calculate the Staging object for.
     * @param environmentIdFrom Source Environment ID
     * @param changesFilter     Optional filter to reduce the found changes to.
     *
     * @return A completable future providing the <code>Staging</code> object when done, or failing if the staging
     *         cannot be calculated for whatever reason.
     */
    CompletableFuture<Staging> prepareStaging(String applicationId, String environmentIdFrom,
            List<Change> changesFilter);

    /**
     * "Prepares" the staging for a given application and a given source environment. This means,
     * <ul>
     * <li>determining the appropriate target environment,</li>
     * <li>calculating the differences between given source and the target environment, for the given application.</li>
     * </ul>
     * If the optional <code>changesFilter</code> is provided and not empty, the result is filtered, and only changes
     * equal to one of the changes in the given filter list are included. Note that this may lead to an inconsistent
     * change set, which may not fully be applied to the target environment successfully. <br>
     * <br>
     * To perform the staging, call <code>perform()</code> on the (asynchronously) returned <code>Staging</code> object.
     *
     * @param environmentIdFrom Source Environment ID
     *
     * @return A completable future providing the <code>Staging</code> object when done, or failing if the staging
     *         cannot be calculated for whatever reason.
     */
    String getNextStage(String environmentIdFrom);

}
