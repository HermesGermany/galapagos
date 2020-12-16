package com.hermesworld.ais.galapagos.applications.controller;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.changes.Change;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonSerialize
public class StagingFilterDto {

	private List<Change> changes;

}
