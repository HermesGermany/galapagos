package com.hermesworld.ais.galapagos.topics.controller;

import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;

@JsonSerialize
@Getter
public class ConsumerRecordDto {

	private String key;

	private String value;

	private long offset;

	private long timestamp;

	private int partition;

	private Map<String, String> headers;

	ConsumerRecordDto(String key, String value, long offset, long timestamp, int partition, Map<String, String> headers) {
		this.key = key;
		this.value = value;
		this.offset = offset;
		this.timestamp = timestamp;
		this.partition = partition;
		this.headers = headers;
	}

}
