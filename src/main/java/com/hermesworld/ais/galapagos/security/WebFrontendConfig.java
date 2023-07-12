package com.hermesworld.ais.galapagos.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextListener;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebMvc
public class WebFrontendConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/app/**").addResourceLocations("classpath:/static/app/");
        registry.addResourceHandler("/assets/**").addResourceLocations("classpath:/static/app/assets/");
    }

    /**
     * This ensures that the current HTTP Request is accessible via ThreadLocal accessory, e.g. in
     * AuditEventRepositoryImpl.
     *
     * @return A new RequestContextListener bean.
     */
    @Bean
    public RequestContextListener requestContextListener() {
        return new RequestContextListener();
    }

}
