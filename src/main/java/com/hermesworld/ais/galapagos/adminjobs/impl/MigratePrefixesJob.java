package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.applications.KnownApplication;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.naming.ApplicationPrefixes;
import com.hermesworld.ais.galapagos.naming.NamingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Admin job for the migration of application metadata and associated ACLs after upgrading to Galapagos 1.8.0.
 * <h2>When to use</h2>
 * <p>
 * <b>Before</b> upgrading to Galapagos 1.8.0, you should run this admin job in "dry mode", to check which Kafka ACLs
 * will be modified - and especially, if any will be deleted. This should normally <b>not</b> be the case - if you
 * encounter any, this should be investigated (manual ACL? Galapagos reconfiguration? Application Name or Aliases
 * changed?).
 * </p>
 * <p>
 * <b>After</b> upgrading to Galapagos 1.8.0, you should immediately run this admin job for every Kafka cluster, to make
 * sure correct ACLs are set in Kafka and users see the rights assigned to their applications in Galapagos.
 * </p>
 * <h2>Risks</h2>
 * <p>
 * As outlined above, there are some situations where ACLs could be <b>removed</b> by this job. If these are critical
 * for running applications, these applications could stop working.
 * </p>
 * <h2>How to use</h2>
 * <p>
 * Add <code>--galapagos.jobs.migrate-prefixes</code> as startup parameter to Galapagos. <br>
 * The job has one required and one optional parameter:
 * <ul>
 * <li><code>--kafka.environment=<i>&lt;id></i> - The ID of the Kafka Environment to migrate application prefixes and 
 * ACLs on, as configured for Galapagos.</li>
 * <li><code>--dry.run</code> - If present, no metadata is changed, and no ACLs are created or deleted, but ACL changes
 * are printed to STDOUT instead.</li>
 * </ul>
 */
@Component
@Slf4j
public class MigratePrefixesJob extends SingleClusterAdminJob {

    private final ApplicationsService applicationsService;

    private final NamingService namingService;

    private final UpdateApplicationAclsJob updateJob;

    @Autowired
    public MigratePrefixesJob(KafkaClusters kafkaClusters, ApplicationsService applicationsService,
            NamingService namingService, UpdateApplicationAclsJob updateJob) {
        super(kafkaClusters);
        this.applicationsService = applicationsService;
        this.namingService = namingService;
        this.updateJob = updateJob;
    }

    @Override
    public String getJobName() {
        return "migrate-prefixes";
    }

    @Override
    protected void runOnCluster(KafkaCluster cluster, ApplicationArguments allArguments) throws Exception {
        boolean dryRun = allArguments.containsOption("dry.run");
        String environmentId = cluster.getId();

        System.out.println("===== MIGRATING Kafka Prefixes for registered Applications on " + environmentId + " =====");
        if (dryRun) {
            updateJob.performUpdate(cluster, id -> getNewMetadata(environmentId, id), true);
        }
        else {
            // update application metadata first
            // repo should already be initialized by application service
            TopicBasedRepository<ApplicationMetadata> repository = cluster.getRepository("application-metadata",
                    ApplicationMetadata.class);

            int updatedMetadata = 0;
            for (KnownApplication application : applicationsService.getKnownApplications(false)) {
                ApplicationMetadata oldMetadata = applicationsService
                        .getApplicationMetadata(environmentId, application.getId()).orElse(null);
                if (oldMetadata != null && mustMigrate(oldMetadata)) {
                    repository.save(toMigratedMetadata(oldMetadata)).get();
                    updatedMetadata++;
                }
            }
            System.out.println(updatedMetadata + " application metadata entries migrated.");

            updateJob.performUpdate(cluster, id -> applicationsService.getApplicationMetadata(environmentId, id),
                    false);
        }
        System.out.println("===== Migration COMPLETE =====");
    }

    private Optional<ApplicationMetadata> getNewMetadata(String environmentId, String applicationId) {
        return applicationsService.getApplicationMetadata(environmentId, applicationId).map(this::toMigratedMetadata);
    }

    @SuppressWarnings("removal")
    private boolean mustMigrate(ApplicationMetadata metadata) {
        ApplicationMetadata newMetadata = toMigratedMetadata(metadata);
        BiFunction<List<String>, List<String>, Boolean> equalPrefixes = (l1, l2) -> {
            if (l1 == null || l2 == null) {
                return false;
            }
            return l1.stream().sorted().collect(Collectors.toList())
                    .equals(l2.stream().sorted().collect(Collectors.toList()));
        };

        return metadata.getTopicPrefix() != null
                || !equalPrefixes.apply(metadata.getInternalTopicPrefixes(), newMetadata.getInternalTopicPrefixes())
                || !equalPrefixes.apply(metadata.getConsumerGroupPrefixes(), newMetadata.getConsumerGroupPrefixes())
                || !equalPrefixes.apply(metadata.getTransactionIdPrefixes(), newMetadata.getTransactionIdPrefixes());
    }

    @SuppressWarnings("removal")
    private ApplicationMetadata toMigratedMetadata(ApplicationMetadata oldMetadata) {
        KnownApplication app = applicationsService.getKnownApplication(oldMetadata.getApplicationId()).orElse(null);
        if (app == null) {
            log.warn("Could not find application info for ID " + oldMetadata.getApplicationId()
                    + ", cannot migrate record");
            return oldMetadata;
        }

        ApplicationPrefixes newPrefixes = namingService.getAllowedPrefixes(app).combineWith(oldMetadata);
        ApplicationMetadata newMetadata = new ApplicationMetadata(oldMetadata);

        List<String> internalTopicPrefixes = new ArrayList<>(newPrefixes.getInternalTopicPrefixes());
        if (!StringUtils.isEmpty(oldMetadata.getTopicPrefix())) {
            internalTopicPrefixes.add(oldMetadata.getTopicPrefix());
        }

        newMetadata.setInternalTopicPrefixes(internalTopicPrefixes.stream().distinct().collect(Collectors.toList()));
        newMetadata.setConsumerGroupPrefixes(newPrefixes.getConsumerGroupPrefixes());
        newMetadata.setTransactionIdPrefixes(newPrefixes.getTransactionIdPrefixes());
        newMetadata.setTopicPrefix(null);

        return newMetadata;
    }

}
