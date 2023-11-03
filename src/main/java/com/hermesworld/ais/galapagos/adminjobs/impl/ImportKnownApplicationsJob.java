package com.hermesworld.ais.galapagos.adminjobs.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.hermesworld.ais.galapagos.adminjobs.AdminJob;
import com.hermesworld.ais.galapagos.applications.BusinessCapability;
import com.hermesworld.ais.galapagos.applications.impl.KnownApplicationImpl;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.util.TopicBasedRepository;
import com.hermesworld.ais.galapagos.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StreamUtils;

/**
 * Admin job to import known applications from a JSON file (or STDIN) to the global Galapagos topic
 * <code>known-applications</code>. This job can be used for development or test instances of Galapagos to import test
 * data, or it can be used on production instances if no direct import on the Kafka topic is possible. <br>
 * The job requires the parameter <code>--applications.import.file=&lt;file></code>. <i>file</i> can also be the special
 * value <code>-</code>, in which case the JSON data is read from STDIN. <br>
 * An optional parameter <code>--remove.missing.applications=true</code> can be used to force removal of applications
 * which are stored in the internal topic, but cannot be found in the JSON data. By default, these applications are
 * <b>not</b> removed. <br>
 * The JSON data must be a valid JSON array. Each element of the JSON array must be a JSON Object which can be parsed as
 * {@link KnownApplicationImpl} instance.
 *
 * @author AlbrechtFlo
 *
 */
@Component
@Slf4j
public class ImportKnownApplicationsJob implements AdminJob {

    private KafkaClusters kafkaClusters;

    public ImportKnownApplicationsJob(KafkaClusters kafkaClusters) {
        this.kafkaClusters = kafkaClusters;
    }

    @Override
    public String getJobName() {
        return "import-known-applications";
    }

    @Override
    public void run(ApplicationArguments allArguments) throws Exception {
        String jsonFile = Optional.ofNullable(allArguments.getOptionValues("applications.import.file"))
                .map(ls -> ls.stream().findFirst().orElse(null)).orElse(null);

        boolean remove = Optional.ofNullable(allArguments.getOptionValues("remove.missing.applications"))
                .map(ls -> ls.stream().findFirst().orElse(null)).map(s -> s == null ? false : Boolean.parseBoolean(s))
                .orElse(false);

        if (ObjectUtils.isEmpty(jsonFile)) {
            throw new IllegalArgumentException("Please provide --applications.import.file=<file> for JSON to import");
        }

        List<KnownApplicationImpl> imported;

        // STDIN also supported!
        if ("-".equals(jsonFile)) {
            imported = readFromStdin();
        }
        else {
            imported = readFromFile(jsonFile);
        }

        TopicBasedRepository<KnownApplicationImpl> repo = kafkaClusters.getGlobalRepository("known-applications",
                KnownApplicationImpl.class);

        // give repo 10 seconds to get all data from Kafka (if any)
        log.info("Waiting for existing known applications to be retrieved from Kafka...");
        try {
            Thread.sleep(Duration.ofSeconds(10).toMillis());
        }
        catch (InterruptedException e) {
            return;
        }

        int cntImported = 0;
        for (KnownApplicationImpl application : imported) {
            boolean shouldImport = repo.getObject(application.getId()).map(app -> !isEqualTo(application, app))
                    .orElse(true);
            if (shouldImport) {
                repo.save(application).get();
                cntImported++;
            }
        }

        Set<String> importedIds = imported.stream().map(app -> app.getId()).collect(Collectors.toSet());

        int cntDeleted = 0;
        if (remove) {
            for (KnownApplicationImpl app : repo.getObjects()) {
                if (!importedIds.contains(app.getId())) {
                    repo.delete(app).get();
                    cntDeleted++;
                }
            }
        }

        System.out.println();
        System.out.println("========================= Known applications IMPORTED ========================");
        System.out.println();
        System.out.println(cntImported + " new application(s) imported.");
        if (remove) {
            System.out.println(cntDeleted + " application(s) removed as they did not exist in JSON data.");
        }
        System.out.println();
        System.out.println("==============================================================================");

    }

    private List<KnownApplicationImpl> readFromStdin() throws IOException {
        return readFromJsonString(StreamUtils.copyToString(System.in, Charset.defaultCharset()));
    }

    private List<KnownApplicationImpl> readFromFile(String file) throws IOException {
        File f = new File(file);
        try (FileInputStream fis = new FileInputStream(f)) {
            return readFromJsonString(StreamUtils.copyToString(fis, StandardCharsets.UTF_8));
        }
    }

    private List<KnownApplicationImpl> readFromJsonString(String data) throws IOException {
        ObjectMapper mapper = JsonUtil.newObjectMapper();
        JavaType tp = TypeFactory.defaultInstance().constructArrayType(KnownApplicationImpl.class);
        KnownApplicationImpl[] values = mapper.readValue(data, tp);
        return Arrays.asList(values);
    }

    private boolean isEqualTo(KnownApplicationImpl imported, KnownApplicationImpl existing) {
        return imported.getName().equals(existing.getName()) && imported.getId().equals(existing.getId())
                && Objects.equals(imported.getInfoUrl(), existing.getInfoUrl())
                && Objects.equals(imported.getAliases(), existing.getAliases())
                && businessCapabilityIsEqual(imported.getBusinessCapabilities(), existing.getBusinessCapabilities());
    }

    private boolean businessCapabilityIsEqual(List<BusinessCapability> imported, List<BusinessCapability> existing) {

        if (imported == null && existing == null) {
            return true;
        }

        if (imported == null || existing == null) {
            return false;
        }

        if (imported.size() != existing.size()) {
            return false;
        }

        for (int i = 0; i < imported.size(); i++) {
            if (!(imported.get(i).getId().equals(existing.get(i).getId()))
                    || !(imported.get(i).getName().equals(existing.get(i).getName()))) {
                return false;
            }
        }

        return true;

    }

}