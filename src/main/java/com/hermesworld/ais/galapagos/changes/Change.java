package com.hermesworld.ais.galapagos.changes;

/**
 * Describes a single change, from Galapagos point of view, on a Kafka cluster. <br>
 * A change can either be used in a Change Log, to describe the sequence of changes applied to a cluster, or for
 * <i>Staging</i>, where the list of <i>required</i> changes to be applied on a target environment are calculated.
 *
 * @author AlbrechtFlo
 *
 */
public interface Change {

	/**
	 * Returns the type of this change.
	 *
	 * @return The type of this change, never <code>null</code>.
	 */
	ChangeType getChangeType();

}
