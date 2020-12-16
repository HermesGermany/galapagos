package com.hermesworld.ais.galapagos;

import java.security.Security;
import java.util.List;

import com.hermesworld.ais.galapagos.adminjobs.AdminJob;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@ServletComponentScan
@SpringBootApplication
@EnableScheduling
public class GalapagosApplication implements ApplicationRunner {

	private static final String ADMIN_JOB_OPTION_PREFIX = "galapagos.jobs.";

	@Autowired
	private List<AdminJob> adminJobs;

	@Autowired
	private ApplicationContext applicationContext;

	public static void main(String[] args) {
		Security.setProperty("crypto.policy", "unlimited");
		Security.addProvider(new BouncyCastleProvider());

		SpringApplication.run(GalapagosApplication.class, args);
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		boolean exit = false;
		for (String optionName : args.getOptionNames()) {
			if (optionName.startsWith(ADMIN_JOB_OPTION_PREFIX)) {
				String jobName = optionName.substring(ADMIN_JOB_OPTION_PREFIX.length());
				AdminJob job = adminJobs.stream().filter(j -> jobName.equals(j.getJobName())).findFirst()
						.orElseThrow(() -> new IllegalArgumentException("Unknown Galapagos Admin job type: " + optionName));
				try {
					job.run(args);
					exit = true;
				}
				catch (Throwable t) {
					t.printStackTrace();
					SpringApplication.exit(applicationContext, () -> 1);
				}
			}
		}

		if (exit) {
			SpringApplication.exit(applicationContext, () -> 0);
		}
	}

}
