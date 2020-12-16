package com.hermesworld.ais.galapagos.adminjobs.impl;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.applications.KnownApplication;
import com.hermesworld.ais.galapagos.applications.impl.UpdateApplicationAclsListener;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.impl.ConnectedKafkaCluster;
import org.apache.kafka.clients.admin.CreateAclsResult;
import org.apache.kafka.clients.admin.DeleteAclsResult;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

/**
 * This admin job "refreshes" the ACLs in the given Kafka Cluster so all required ACLs for all applications - according
 * to Galapagos Metadata - are present. It also <b>removes</b> superfluous ACLs from Kafka set for the applications
 * known to Galapagos (ACLs not belonging to one of the applications registered in Galapagos are not changed). <br>
 * This admin job is particularly useful if new rights have been added to Galapagos logic (e.g. for Transactional IDs)
 * or if, for some reason, the ACLs in Kafka have been modified / corrupted. <br><br>
 * The job has one required and one optional parameter:
 * <ul>
 * <li><code>--kafka.environment=<i>&lt;id></i> - The ID of the Kafka Environment to restore the application ACLs on, as
 * configured for Galapagos.</li>
 * <li><code>--dry.run</code> - If present, no ACLs are created or deleted, but these actions are printed to STDOUT
 * instead.</li>
 * </ul>
 */
@Component
public class UpdateApplicationAclsJob extends SingleClusterAdminJob {

    private final UpdateApplicationAclsListener aclUpdater;

    private final ApplicationsService applicationsService;

    @Autowired
    public UpdateApplicationAclsJob(KafkaClusters kafkaClusters, UpdateApplicationAclsListener aclUpdater,
        ApplicationsService applicationsService) {
        super(kafkaClusters);
        this.aclUpdater = aclUpdater;
        this.applicationsService = applicationsService;
    }

    @Override
    public String getJobName() {
        return "update-application-acls";
    }

    @Override
    public void runOnCluster(KafkaCluster cluster, ApplicationArguments allArguments) throws Exception {
        Map<String, KnownApplication> applications = applicationsService.getKnownApplications(false).stream()
            .collect(Collectors.toMap(KnownApplication::getId, Function.identity()));

        List<AclBinding> dryRunCreatedAcls = new ArrayList<>();
        List<AclBindingFilter> dryRunDeletedAcls = new ArrayList<>();

        boolean dryRun = allArguments.containsOption("dry.run");

        if (dryRun) {
            ((ConnectedKafkaCluster) cluster).wrapAdminClient(client -> new NoUpdatesAdminClient(client) {
                @Override
                public CreateAclsResult createAcls(Collection<AclBinding> acls) {
                    dryRunCreatedAcls.addAll(acls);
                    return client.createAcls(List.of());
                }

                @Override
                public DeleteAclsResult deleteAcls(Collection<AclBindingFilter> filters) {
                    dryRunDeletedAcls.addAll(filters);
                    return client.deleteAcls(List.of());
                }
            });
        }

        for (String id : applications.keySet()) {
            Optional<ApplicationMetadata> opMeta = applicationsService.getApplicationMetadata(cluster.getId(), id);
            if (opMeta.isPresent()) {
                if (!dryRun) {
                    System.out.println("Updating ACLs for application " + applications.get(id).getName());
                }
                updateApplicationAcl(cluster, opMeta.get());
            }
        }

        if (dryRun) {
            System.out.println("Would CREATE the following ACLs:");
            dryRunCreatedAcls.forEach(System.out::println);
            System.out.println();
            System.out.println("Would DELETE the following ACLs:");
            dryRunDeletedAcls.forEach(System.out::println);
        }

        System.out.println();
        System.out.println("==================== Update of Application ACLs COMPLETE ====================");
        System.out.println();
    }

    private void updateApplicationAcl(KafkaCluster cluster,
        ApplicationMetadata metadata) throws ExecutionException, InterruptedException {
        cluster.updateUserAcls(aclUpdater.getApplicationUser(metadata, cluster.getId())).get();
    }
}
