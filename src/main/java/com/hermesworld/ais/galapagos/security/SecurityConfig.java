package com.hermesworld.ais.galapagos.security;

import com.hermesworld.ais.galapagos.security.config.GalapagosSecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;

import java.util.Collection;
import java.util.Locale;
import java.util.stream.Collectors;

@Configuration
@EnableMethodSecurity(securedEnabled = true, prePostEnabled = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, GalapagosSecurityProperties config) throws Exception {
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(reg -> reg.requestMatchers("/api/**").hasRole("USER").anyRequest().permitAll());
        http.oauth2ResourceServer(conf -> conf.jwt(jwtCustomizer(config)));
        return http.build();
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new RegisterSessionAuthenticationStrategy(new SessionRegistryImpl());
    }

    private Customizer<OAuth2ResourceServerConfigurer<HttpSecurity>.JwtConfigurer> jwtCustomizer(
            GalapagosSecurityProperties config) {
        return jwtConfigurer -> jwtConfigurer.jwtAuthenticationConverter(jwtAuthenticationConverter(config));
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter(GalapagosSecurityProperties config) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setPrincipalClaimName(config.getJwtUserNameClaim());

        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName(config.getJwtRoleClaim());
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        converter.setJwtGrantedAuthoritiesConverter(new UpperCaseJwtGrantedAuthoritiesConverter(authoritiesConverter));

        return converter;
    }

    private record UpperCaseJwtGrantedAuthoritiesConverter(JwtGrantedAuthoritiesConverter delegate)
            implements Converter<Jwt, Collection<GrantedAuthority>> {

        @Override
        public Collection<GrantedAuthority> convert(@NonNull Jwt source) {
            return mapToUpperCase(delegate.convert(source));
        }

        private Collection<GrantedAuthority> mapToUpperCase(Collection<GrantedAuthority> authorities) {
            return authorities.stream().map(a -> new SimpleGrantedAuthority(a.getAuthority().toUpperCase(Locale.US)))
                    .collect(Collectors.toSet());
        }
    }

}
