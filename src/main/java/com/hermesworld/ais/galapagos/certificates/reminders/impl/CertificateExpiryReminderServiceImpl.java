package com.hermesworld.ais.galapagos.certificates.reminders.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.certificates.reminders.CertificateExpiryReminder;
import com.hermesworld.ais.galapagos.certificates.reminders.CertificateExpiryReminderService;
import com.hermesworld.ais.galapagos.certificates.reminders.ReminderType;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;
import com.hermesworld.ais.galapagos.kafka.util.InitPerCluster;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class CertificateExpiryReminderServiceImpl implements CertificateExpiryReminderService, InitPerCluster {

    private final KafkaClusters kafkaClusters;

    private final ApplicationsService applicationsService;

    private static final String REPOSITORY_NAME = "reminders";

    public CertificateExpiryReminderServiceImpl(KafkaClusters kafkaClusters, ApplicationsService applicationsService) {
        this.kafkaClusters = kafkaClusters;
        this.applicationsService = applicationsService;
    }

    @Override
    public void init(KafkaCluster cluster) {
        getRepository(cluster).getObjects();
    }

    @Override
    public List<CertificateExpiryReminder> calculateDueCertificateReminders() {
        List<CertificateExpiryReminder> result = new ArrayList<>();

        Instant now = Instant.now();

        for (KafkaCluster cluster : kafkaClusters.getEnvironments()) {
            KafkaEnvironmentConfig envMeta = kafkaClusters.getEnvironmentMetadata(cluster.getId()).orElse(null);
            KafkaAuthenticationModule authModule = kafkaClusters.getAuthenticationModule(cluster.getId()).orElse(null);

            if (envMeta == null || authModule == null || !"certificates".equals(envMeta.getAuthenticationMode())) {
                continue;
            }
            List<ApplicationMetadata> allMetadata = applicationsService.getAllApplicationMetadata(cluster.getId());

            Collection<ReminderMetadata> sentReminders = getRepository(cluster).getObjects();

            for (ApplicationMetadata app : allMetadata) {
                if (StringUtils.isEmpty(app.getAuthenticationJson())) {
                    continue;
                }
                JSONObject authData;
                try {
                    authData = new JSONObject(app.getAuthenticationJson());
                }
                catch (JSONException e) {
                    log.error("Invalid JSON in authentication data of application " + app.getApplicationId(), e);
                    continue;
                }
                Optional<Instant> certExpires = authModule.extractExpiryDate(authData);
                if (certExpires.isEmpty()) {
                    log.warn("No expiresAt found in authentication data of application " + app.getApplicationId());
                    continue;
                }

                for (ReminderType reminderType : ReminderType.values()) {
                    if (certExpires.get().isBefore(reminderType.calculateInstant(now))) {
                        CertificateExpiryReminder reminder = new CertificateExpiryReminder(app.getApplicationId(),
                                cluster.getId(), reminderType);

                        if (!containsReminder(sentReminders, reminder)) {
                            result.add(reminder);
                        }
                        // break in any case, as, if a reminder e.g. for one_week has been sent already, we won't return
                        // a reminder for one_month or three_months (although it has never been sent).
                        break;
                    }
                }
            }
        }

        return result;
    }

    @Override
    public void markReminderSentOut(CertificateExpiryReminder reminder) {
        KafkaCluster clusterOfEnv = kafkaClusters.getEnvironment(reminder.getEnvironmentId()).orElseThrow();

        ReminderMetadata metadata = new ReminderMetadata();
        metadata.setApplicationId(reminder.getApplicationId());
        metadata.setReminderType(reminder.getReminderType());
        metadata.setReminderId(UUID.randomUUID().toString());

        getRepository(clusterOfEnv).save(metadata);
    }

    private boolean containsReminder(Collection<ReminderMetadata> metadatas, CertificateExpiryReminder reminder) {
        return metadatas.stream().anyMatch(meta -> meta.getApplicationId().equals(reminder.getApplicationId())
                && meta.getReminderType().equals(reminder.getReminderType()));
    }

    private TopicBasedRepository<ReminderMetadata> getRepository(KafkaCluster cluster) {
        return cluster.getRepository(REPOSITORY_NAME, ReminderMetadata.class);
    }

}
