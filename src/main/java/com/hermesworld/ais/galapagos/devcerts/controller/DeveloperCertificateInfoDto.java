package com.hermesworld.ais.galapagos.devcerts.controller;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;

@JsonSerialize
@Getter
public class DeveloperCertificateInfoDto {

	private String dn;

	@JsonFormat(shape = Shape.STRING)
	private Instant expiresAt;

	public DeveloperCertificateInfoDto(String dn, Instant expiresAt) {
		this.dn = dn;
		this.expiresAt = expiresAt;
	}

}
