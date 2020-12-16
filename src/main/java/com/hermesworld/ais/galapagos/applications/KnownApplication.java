package com.hermesworld.ais.galapagos.applications;

import java.net.URL;
import java.util.List;
import java.util.Set;

/**
 * An application "known" to Galapagos. The list of all known applications form the basis from which the users can
 * select "their" applications and create Application Owner Requests for. Known applications must be registered on a
 * Kafka Cluster using
 * {@link ApplicationsService#createApplicationCertificateAndPrivateKey(String, String, String, java.io.OutputStream)}
 * or
 * {@link ApplicationsService#createApplicationCertificateFromCsr(String, String, String, String, java.io.OutputStream)}
 * to retrieve a Client Certificate for this application.
 *
 * @author AlbrechtFlo
 *
 */
public interface KnownApplication {

	/**
	 * The ID of this application. It can be any non-empty string, but must be constant over time and unique throughout all
	 * known applications.
	 *
	 * @return The ID of this application, never <code>null</code> nor an empty string.
	 */
	String getId();

	/**
	 * The human-readable name of this application. While the ID is constant for the "same" application, this name may
	 * change over time.
	 *
	 * @return The human-readable name of this application, never <code>null</code> nor an empty string.
	 */
	String getName();

	/**
	 * The (potentially empty) set of aliases for this application. An application may have some different names or commonly
	 * used abbreviations. These aliases may also be used for some Kafka objects where usually the name of the application
	 * has to be used (e.g. in names of internal topics).
	 *
	 * @return The (potentially empty) set of aliases for this application, never <code>null</code>.
	 */
	Set<String> getAliases();

	/**
	 * A URL providing more information about this application, usually in an Enterprise Architecture Tool, e.g. LeanIX.
	 *
	 * @return A URL providing more information about this application, or <code>null</code>.
	 */
	URL getInfoUrl();

	/**
	 * The (potentially empty) list of business capabilities which are supported by this application. For the naming schema
	 * of "API topics", the business capabilities are a central part. Applications which do not support any business
	 * capability cannot provide (create) API topics (but internal topics).
	 *
	 * @return The (potentially empty) list of business capabilities supported by this application, never <code>null</code>.
	 */
	List<BusinessCapability> getBusinessCapabilities();

}
