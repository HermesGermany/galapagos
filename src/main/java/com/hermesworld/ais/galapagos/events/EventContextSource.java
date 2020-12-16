package com.hermesworld.ais.galapagos.events;

import java.util.Map;

/**
 * Interface for components wanting to contribute to the context of a Galapagos event. Usually, this is used to store
 * some thread-local attributes derived from Security contexts or Request attributes.
 */
public interface EventContextSource {

	/**
	 * Builds and returns the map of context values provided by this context source.
	 *
	 * @return A (possibly empty) map of context values, never <code>null</code>.
	 */
	Map<String, Object> getContextValues();

}
