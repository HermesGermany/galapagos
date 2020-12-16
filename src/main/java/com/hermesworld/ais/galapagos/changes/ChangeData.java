package com.hermesworld.ais.galapagos.changes;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.util.HasKey;
import lombok.Getter;
import lombok.Setter;

@JsonSerialize
@Getter
@Setter
public class ChangeData implements Comparable<ChangeData>, HasKey {

	private String id;

	@JsonFormat(shape = Shape.STRING)
	private ZonedDateTime timestamp;

	private String principal;

	private String principalFullName;

	private Change change;

	@Override
	public String key() {
		return id;
	}

	@Override
	public int compareTo(ChangeData o) {
		if (o == null) {
			return -1;
		}
		if (timestamp == null) {
			return o.timestamp == null ? 0 : -1;
		}
		if (o.timestamp == null) {
			return 1;
		}
		return timestamp.compareTo(o.timestamp);
	}

}
