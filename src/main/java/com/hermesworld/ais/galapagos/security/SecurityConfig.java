package com.hermesworld.ais.galapagos.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;

@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter {


    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // TODO is CSRF necessary for a stateless backend without sessions?
        http.csrf().disable();
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.NEVER);
        http.authorizeRequests().antMatchers("/api/**").hasRole("USER").and().oauth2Login();
    }


}
