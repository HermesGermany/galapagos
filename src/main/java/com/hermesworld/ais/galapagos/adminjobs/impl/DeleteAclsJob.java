package com.hermesworld.ais.galapagos.adminjobs.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.KafkaUser;
import org.apache.kafka.common.acl.AclBinding;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

/**
 * Admin job to explicitly delete ACLs from a Kafka Cluster. This job is useful if something went terribly wrong with
 * Galapagos, or if some rights have to be revoked quickly. <br>
 * It can also be used to remove the ACLs previously generated with the {@link GenerateToolingCertificateJob}. <br>
 * The job requires two parameters:
 * <ul>
 * <li><code>--certificate.dn=<i>&lt;dn></i> - The Distinguished Name of the certificate to remove the associated ACLs
 * of.</li>
 * <li><code>--kafka.environment=<i>&lt;id></i> - The ID of the Kafka Environment to operate on, as configured for
 * Galapagos.</li>
 * </ul>
 *
 * @author AlbrechtFlo
 *
 */
@Component
public class DeleteAclsJob extends SingleClusterAdminJob {

    public DeleteAclsJob(KafkaClusters kafkaClusters) {
        super(kafkaClusters);
    }

    @Override
    public String getJobName() {
        return "delete-acls";
    }

    @Override
    public void runOnCluster(KafkaCluster cluster, ApplicationArguments allArguments) throws Exception {
        String certificateDn = Optional.ofNullable(allArguments.getOptionValues("certificate.dn"))
                .flatMap(ls -> ls.stream().findFirst()).orElse(null);

        if (ObjectUtils.isEmpty(certificateDn)) {
            throw new IllegalArgumentException("Please provide --certificate.dn=<dn> for DN of certificate.");
        }

        cluster.removeUserAcls(new DummyKafkaUser(certificateDn)).get();

        System.out.println();
        System.out.println("========================== Certificate ACLs DELETED ==========================");
        System.out.println();
        System.out.println("All ACLs for certificate " + certificateDn + " have been deleted on Kafka Environment "
                + cluster.getId());
        System.out.println();
        System.out.println("==============================================================================");
    }

    private static class DummyKafkaUser implements KafkaUser {

        private final String dn;

        public DummyKafkaUser(String dn) {
            this.dn = dn;
        }

        @Override
        public String getKafkaUserName() {
            return "User:" + dn;
        }

        @Override
        public Collection<AclBinding> getRequiredAclBindings() {
            return Collections.emptyList();
        }
    }

}
