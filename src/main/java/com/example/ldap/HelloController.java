package com.example.ldap;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
