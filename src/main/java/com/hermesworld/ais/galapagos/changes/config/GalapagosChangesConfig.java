package com.hermesworld.ais.galapagos.changes.config;

import com.hermesworld.ais.galapagos.uisupport.controller.ProfilePicture;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("galapagos.changelog")
@Getter
@Setter
public class GalapagosChangesConfig {
    private int entries;
    private int minDays;
    private ProfilePicture profilePicture;
    private ProfilePicture defaultPicture;
}
