package com.hermesworld.ais.galapagos.util;

import java.time.ZonedDateTime;

/**
 * Component interface to retrieve a current timestamp. All components should use this interface to get a timestamp
 * instead of directly calling {@link ZonedDateTime#now()} to enable unit tests which replace this component with a
 * mock.
 *
 * @author AlbrechtFlo
 *
 */
public interface TimeService {

	ZonedDateTime getTimestamp();

}
