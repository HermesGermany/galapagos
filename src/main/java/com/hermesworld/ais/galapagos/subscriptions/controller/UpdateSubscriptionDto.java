package com.hermesworld.ais.galapagos.subscriptions.controller;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionState;

import lombok.Getter;
import lombok.Setter;

@JsonSerialize
@Getter
@Setter
public class UpdateSubscriptionDto {

    private SubscriptionState newState;

}
