package com.hermesworld.ais.galapagos.security;

import org.keycloak.adapters.springsecurity.KeycloakConfiguration;
import org.keycloak.adapters.springsecurity.authentication.KeycloakAuthenticationProvider;
import org.keycloak.adapters.springsecurity.config.KeycloakWebSecurityConfigurerAdapter;
import org.keycloak.adapters.springsecurity.filter.KeycloakAuthenticatedActionsFilter;
import org.keycloak.adapters.springsecurity.filter.KeycloakAuthenticationProcessingFilter;
import org.keycloak.adapters.springsecurity.filter.KeycloakPreAuthActionsFilter;
import org.keycloak.adapters.springsecurity.filter.KeycloakSecurityContextRequestFilter;
import org.keycloak.adapters.springsecurity.management.HttpSessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.mapping.SimpleAuthorityMapper;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.web.context.request.RequestContextListener;

//@Configuration
//@KeycloakConfiguration
//@EnableMethodSecurity(securedEnabled = true, prePostEnabled = false)
public class SecurityConfig {

    /**
     * Registers the KeycloakAuthenticationProvider with the authentication manager.
     */
//    @Autowired
//    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
//        KeycloakAuthenticationProvider provider = keycloakAuthenticationProvider();
//        SimpleAuthorityMapper mapper = new SimpleAuthorityMapper();
//        mapper.setConvertToUpperCase(true);
//        mapper.setConvertToLowerCase(false);
//        provider.setGrantedAuthoritiesMapper(mapper);
//        auth.authenticationProvider(provider);
//    }
//
//    @Bean
//    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
//        super.configure(http);
//        // TODO is CSRF necessary for a stateless backend without sessions?
//        http.csrf().disable();
//        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
//        http.authorizeRequests().antMatchers("/api/**").hasRole("USER").anyRequest().permitAll();
//        return http.build();
//    }
//
//    // The following four beans shall avoid a double registration of the Keycloak filters.
//    // See https://www.keycloak.org/docs/latest/securing_apps/index.html#spring-boot-integration for details.
//    // (Google Keywords: keycloak "avoid double bean registration")
//
//    @Bean
//    public FilterRegistrationBean<KeycloakAuthenticationProcessingFilter> keycloakAuthenticationProcessingFilterRegistrationBean(
//            KeycloakAuthenticationProcessingFilter filter) {
//        FilterRegistrationBean<KeycloakAuthenticationProcessingFilter> registrationBean = new FilterRegistrationBean<>(
//                filter);
//        registrationBean.setEnabled(false);
//        return registrationBean;
//    }
//
//    @Bean
//    public FilterRegistrationBean<KeycloakPreAuthActionsFilter> keycloakPreAuthActionsFilterRegistrationBean(
//            KeycloakPreAuthActionsFilter filter) {
//        FilterRegistrationBean<KeycloakPreAuthActionsFilter> registrationBean = new FilterRegistrationBean<>(filter);
//        registrationBean.setEnabled(false);
//        return registrationBean;
//    }
//
//    @Bean
//    public FilterRegistrationBean<KeycloakAuthenticatedActionsFilter> keycloakAuthenticatedActionsFilterBean(
//            KeycloakAuthenticatedActionsFilter filter) {
//        FilterRegistrationBean<KeycloakAuthenticatedActionsFilter> registrationBean = new FilterRegistrationBean<>(
//                filter);
//        registrationBean.setEnabled(false);
//        return registrationBean;
//    }
//
//    @Bean
//    public FilterRegistrationBean<KeycloakSecurityContextRequestFilter> keycloakSecurityContextRequestFilterBean(
//            KeycloakSecurityContextRequestFilter filter) {
//        FilterRegistrationBean<KeycloakSecurityContextRequestFilter> registrationBean = new FilterRegistrationBean<>(
//                filter);
//        registrationBean.setEnabled(false);
//        return registrationBean;
//    }
//
//    @Override
//    @Bean
//    @ConditionalOnMissingBean(HttpSessionManager.class)
//    protected HttpSessionManager httpSessionManager() {
//        // this method only exists for the @ConditionalOnMissingBean annotation, which is missing from the base class.
//        return super.httpSessionManager();
//    }
//
//    @Bean
//    public RequestContextListener requestContextListener() {
//        return new RequestContextListener();
//    }
//
//    @Override
//    protected SessionAuthenticationStrategy sessionAuthenticationStrategy() {
//        // this makes sure that no JSESSIONID cookie is sent. Keycloak authentication of the token is done with each
//        // request, as
//        // it should be.
//        return new NullAuthenticatedSessionStrategy();
//    }

}