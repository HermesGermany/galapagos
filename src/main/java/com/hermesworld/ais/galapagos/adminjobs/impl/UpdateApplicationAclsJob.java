package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.applications.KnownApplication;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.KafkaUser;
import com.hermesworld.ais.galapagos.kafka.impl.ConnectedKafkaCluster;
import com.hermesworld.ais.galapagos.kafka.util.AclSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.json.JSONException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This admin job "refreshes" the ACLs in the given Kafka Cluster so all required ACLs for all applications - according
 * to Galapagos Metadata - are present. It also <b>removes</b> superfluous ACLs from Kafka set for the applications
 * known to Galapagos (ACLs not belonging to one of the applications registered in Galapagos are not changed). <br>
 * This admin job is particularly useful if new rights have been added to Galapagos logic (e.g. for Transactional IDs)
 * or if, for some reason, the ACLs in Kafka have been modified / corrupted. <br>
 * <br>
 * The job has one required and one optional parameter:
 * <ul>
 * <li><code>--kafka.environment=<i>&lt;id></i> - The ID of the Kafka Environment to restore the application ACLs on, as
 * configured for Galapagos.</li>
 * <li><code>--dry.run</code> - If present, no ACLs are created or deleted, but these actions are printed to STDOUT
 * instead.</li>
 * </ul>
 */
@Component
@Slf4j
public class UpdateApplicationAclsJob extends SingleClusterAdminJob {

    private final AclSupport aclSupport;

    private final ApplicationsService applicationsService;

    public UpdateApplicationAclsJob(KafkaClusters kafkaClusters, AclSupport aclSupport,
            ApplicationsService applicationsService) {
        super(kafkaClusters);
        this.aclSupport = aclSupport;
        this.applicationsService = applicationsService;
    }

    @Override
    public String getJobName() {
        return "update-application-acls";
    }

    @Override
    public void runOnCluster(KafkaCluster cluster, ApplicationArguments allArguments) throws Exception {
        boolean dryRun = allArguments.containsOption("dry.run");

        System.out.println("Waiting additional 10 seconds to wait for additional Kafka connection initializations...");
        Thread.sleep(10000);

        performUpdate(cluster, id -> applicationsService.getApplicationMetadata(cluster.getId(), id), dryRun);

        System.out.println();
        System.out.println("==================== Update of Application ACLs COMPLETE ====================");
        System.out.println();
    }

    void performUpdate(KafkaCluster cluster, Function<String, Optional<ApplicationMetadata>> metadataSource,
            boolean dryRun) throws Exception {
        Map<String, KnownApplication> applications = applicationsService.getKnownApplications(false).stream()
                .collect(Collectors.toMap(KnownApplication::getId, Function.identity()));

        List<AclBinding> dryRunCreatedAcls = new ArrayList<>();
        List<AclBindingFilter> dryRunDeletedAcls = new ArrayList<>();

        if (dryRun) {
            ((ConnectedKafkaCluster) cluster).wrapAdminClient(client -> new NoUpdatesAdminClient(client) {
                @Override
                public KafkaFuture<Void> createAcls(Collection<AclBinding> acls) {
                    dryRunCreatedAcls.addAll(acls);
                    return client.createAcls(List.of());
                }

                @Override
                public KafkaFuture<Collection<AclBinding>> deleteAcls(Collection<AclBindingFilter> filters) {
                    dryRunDeletedAcls.addAll(filters);
                    return client.deleteAcls(List.of());
                }
            });
        }

        for (String id : applications.keySet()) {
            Optional<ApplicationMetadata> opMeta = metadataSource.apply(id);
            if (opMeta.isPresent()) {
                if (!dryRun) {
                    System.out.println("Updating ACLs for application " + applications.get(id).getName());
                }
                else {
                    System.out.println("Following ACLs are required for " + applications.get(id).getName());
                    try {
                        System.out.println(new ToolingUser(opMeta.get(), cluster.getId(),
                                kafkaClusters.getAuthenticationModule(cluster.getId()).orElseThrow(), aclSupport)
                                .getRequiredAclBindings());
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }
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

    }

    private void updateApplicationAcl(KafkaCluster cluster, ApplicationMetadata metadata)
            throws ExecutionException, InterruptedException {
        KafkaUser user = new ToolingUser(metadata, cluster.getId(),
                kafkaClusters.getAuthenticationModule(cluster.getId()).orElseThrow(), aclSupport);
        try {
            if (StringUtils.hasLength(user.getKafkaUserName())) {
                cluster.updateUserAcls(user).get();
            }
        }
        catch (JSONException e) {
            log.error("Could not update ACLs for application {}", metadata.getApplicationId(), e);
        }
    }
}
