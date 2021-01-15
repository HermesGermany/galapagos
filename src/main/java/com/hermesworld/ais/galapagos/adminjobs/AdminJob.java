package com.hermesworld.ais.galapagos.adminjobs;

import org.springframework.boot.ApplicationArguments;

/**
 * Interface for admin jobs which are run via command line, but require full Spring & Galapagos stack (especially Kafka
 * connections) to start up. <br>
 * To run an admin job, invoke Galapagos with the special parameter <code>--galapagos.jobs.<i>&lt;jobName></i></code>,
 * e.g. <code>--galapagos.jobs.import-known-applications</code>. You can find the name of each job in the subclasses of
 * this interface. Required and optional additional parameters are also documented in the subclasses. <br>
 * This pattern follows <a href="https://12factor.net/admin-processes">principle #12</a> of <i>twelve-factor apps</i>,
 * delegating one-off or scripted admin jobs to separate process instances, but in an identical environment and with the
 * same configuration as the production application.
 *
 * @author AlbrechtFlo
 *
 */
public interface AdminJob {

    String getJobName();

    void run(ApplicationArguments allArguments) throws Exception;

}
