package com.hermesworld.ais.galapagos.applications.controller;

import com.hermesworld.ais.galapagos.applications.RequestState;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateApplicationOwnerRequestDto {

	private RequestState newState;

}
