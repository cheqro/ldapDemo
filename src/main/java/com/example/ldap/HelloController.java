package com.example.ldap;


import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class HelloController {

    @GetMapping("/public")
    public String publicEndpoint() {
        return "Public endpoint: access granted to everyone.";
    }

    @GetMapping("/user")
    public String userEndpoint() {
        return "User endpoint: access granted to authenticated users.";
    }

    @GetMapping("/admin")
    public String adminEndpoint() {
        return "Admin endpoint: access granted to administrators.";
    }

    @GetMapping("/debug/auth")
    public Map<String, Object> debug(Authentication authentication) {
        System.out.printf("auth" + authentication);
        return Map.of(
                "username", authentication.getName(),
                "authorities", authentication.getAuthorities()
                        .stream()
                        .map(Object::toString)
                        .collect(Collectors.toList())
        );
    }
}
