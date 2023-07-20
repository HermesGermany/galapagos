package com.hermesworld.ais.galapagos;

import com.hermesworld.ais.galapagos.security.SecurityConfig;
import com.hermesworld.ais.galapagos.security.impl.OAuthConfigController;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration
public class GalapagosTestConfig {

    @MockBean
    private OAuthConfigController mockController;

    @MockBean
    private SecurityConfig securityConfig;

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;
}
