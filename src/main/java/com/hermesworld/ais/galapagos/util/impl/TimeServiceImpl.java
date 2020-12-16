package com.hermesworld.ais.galapagos.util.impl;

import java.time.ZonedDateTime;

import org.springframework.stereotype.Component;

import com.hermesworld.ais.galapagos.util.TimeService;

@Component
public class TimeServiceImpl implements TimeService {

	@Override
	public ZonedDateTime getTimestamp() {
		return ZonedDateTime.now();
	}

}
