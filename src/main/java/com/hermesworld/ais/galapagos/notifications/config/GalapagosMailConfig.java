package com.hermesworld.ais.galapagos.notifications.config;

import jakarta.mail.internet.InternetAddress;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties("galapagos.mail")
@Getter
@Setter
public class GalapagosMailConfig {
    private InternetAddress sender;

    private List<InternetAddress> adminRecipients;

    private String defaultMailLanguage;
}
