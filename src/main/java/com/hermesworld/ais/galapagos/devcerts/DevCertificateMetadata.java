package com.hermesworld.ais.galapagos.devcerts;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.util.HasKey;
import lombok.Getter;
import lombok.Setter;

@JsonSerialize
@Getter
@Setter
public class DevCertificateMetadata implements HasKey {

	private String userName;

	private String certificateDn;

	@JsonFormat(shape = Shape.STRING)
	private Instant expiryDate;

	@Override
	public String key() {
		return userName;
	}

}
