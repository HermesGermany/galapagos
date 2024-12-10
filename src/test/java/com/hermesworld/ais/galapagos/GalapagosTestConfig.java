package com.hermesworld.ais.galapagos;

import com.hermesworld.ais.galapagos.security.SecurityConfig;
import com.hermesworld.ais.galapagos.security.impl.OAuthConfigController;
import org.springframework.boot.test.mock.mockito.MockBean;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@SuppressWarnings("removal")
@Configuration
public class GalapagosTestConfig {

    // we have to use MockBean because of https://github.com/spring-projects/spring-framework/issues/33934
    // (Spring Boot team rejects to make MockitoBean work here)

    @MockBean
    private OAuthConfigController mockController;

    @MockBean
    private SecurityConfig securityConfig;

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;
}
