package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.util.AclSupport;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

/**
 * This admin job "resets" the Prefixes and ACLs in the given Kafka Cluster for the given application. <br>
 * <h2>When to use</h2>
 * <p>
 * This admin job can e.g. be used when the Aliases of an application have changed so drastically that you want to
 * remove the prefixes and ACLs associated with the previous aliases from Galapagos and Kafka. By default, Galapagos
 * just <b>adds</b> prefix rights, but never removes them.
 * </p>
 * <h2>Risks</h2>
 * <p>
 * The application will <b>immediately lose</b> access to the prefixes associated with previous, but no longer valid
 * application aliases. If the running instance of the application currently still uses topics or consumer groups with
 * such a prefix, you should write and run migration code before performing this reset. If in doubt, <b>do not run</b>
 * this admin job.
 * </p>
 * <h2>How to use</h2>
 * <p>
 * Add <code>--galapagos.jobs.reset-application-prefixes</code> as startup parameter to Galapagos. <br>
 * The job has two required parameters:
 * <ul>
 * <li><code>--kafka.environment=<i>&lt;id></i> - The ID of the Kafka Environment to reset application prefixes and
 * associated ACLs on, as configured for Galapagos.</li>
 * <li><code>--application.id</code> - ID of the application to reset, as stored in Galapagos. If you do not know how to
 * retrieve this ID, you most likely should better not execute this admin job (see risks above).</li>
 * </ul>
 * </p>
 */
@Component
public class ResetApplicationPrefixesJob extends SingleClusterAdminJob {

    private final ApplicationsService applicationsService;

    private final AclSupport aclSupport;

    public ResetApplicationPrefixesJob(KafkaClusters kafkaClusters, ApplicationsService applicationsService,
            AclSupport aclSupport) {
        super(kafkaClusters);
        this.applicationsService = applicationsService;
        this.aclSupport = aclSupport;
    }

    @Override
    public String getJobName() {
        return "reset-application-prefixes";
    }

    @Override
    protected void runOnCluster(KafkaCluster cluster, ApplicationArguments allArguments) throws Exception {
        String applicationId = allArguments.getOptionValues("application.id").stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Please provide required parameter --application.id"));

        try {
            System.out.println("===== Resetting Prefixes and ACLs for Application " + applicationId + " =====");
            applicationsService.resetApplicationPrefixes(cluster.getId(), applicationId)
                    .thenCompose(metadata -> cluster.updateUserAcls(new ToolingUser(metadata, cluster.getId(),
                            kafkaClusters.getAuthenticationModule(cluster.getId()).orElseThrow(), aclSupport)))
                    .get();
            System.out.println("===== Prefixes and ACL Reset SUCCESSFUL =====");
        }
        catch (ExecutionException e) {
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }
}
