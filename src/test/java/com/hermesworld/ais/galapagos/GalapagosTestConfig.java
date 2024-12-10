package com.hermesworld.ais.galapagos;

import com.hermesworld.ais.galapagos.security.SecurityConfig;
import com.hermesworld.ais.galapagos.security.impl.OAuthConfigController;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration
public class GalapagosTestConfig {

    @MockitoBean
    private OAuthConfigController mockController;

    @MockitoBean
    private SecurityConfig securityConfig;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;
}
