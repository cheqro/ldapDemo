package com.example.ldap;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.ldap.LdapBindAuthenticationManagerFactory;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.security.ldap.server.UnboundIdContainer;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.ldap.core.support.BaseLdapPathContextSource;

@Configuration
public class SecurityConfig {

    @Bean
    public BaseLdapPathContextSource contextSource() {
        DefaultSpringSecurityContextSource contextSource =
                new DefaultSpringSecurityContextSource(
                        "ldap://localhost:389/dc=company,dc=com"
                );

        // Compte technique utilisé pour rechercher les utilisateurs.
        contextSource.setUserDn("cn=admin,dc=company,dc=com");
        contextSource.setPassword("admin123");

        return contextSource;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            BaseLdapPathContextSource contextSource) {

        // Les groupes sont recherchés sous ou=groups.
        DefaultLdapAuthoritiesPopulator authoritiesPopulator =
                new DefaultLdapAuthoritiesPopulator(
                        contextSource,
                        "ou=groups"
                );

        // Recherche les groupes dont l'attribut member contient le DN
        // de l'utilisateur authentifié.
        authoritiesPopulator.setGroupSearchFilter("(member={0})");
        authoritiesPopulator.setGroupRoleAttribute("cn");
        authoritiesPopulator.setRolePrefix("ROLE_");
        authoritiesPopulator.setConvertToUpperCase(false);

        LdapBindAuthenticationManagerFactory factory =
                new LdapBindAuthenticationManagerFactory(contextSource);

        factory.setUserSearchBase("ou=users");
        factory.setUserSearchFilter("(uid={0})");
        factory.setLdapAuthoritiesPopulator(authoritiesPopulator);

        return factory.createAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/public").permitAll()
                        .requestMatchers("/admin").hasRole("APP_ADMINS")
                        .requestMatchers("/user")
                        .hasAnyRole("APP_ADMINS", "APP_USERS")
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
